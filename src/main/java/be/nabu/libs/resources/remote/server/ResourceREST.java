package be.nabu.libs.resources.remote.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import be.nabu.libs.http.HTTPException;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.security.DigestAlgorithm;
import be.nabu.utils.security.SecurityUtils;

@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public class ResourceREST {
	
	@Context
	private ResourceContainer<?> root;
	
	@Path("/exists")
	@GET
	public boolean existsRoot() throws IOException {
		return root != null;
	}
	
	@Path("/exists/{path : .+}")
	@GET
	public boolean exists(@PathParam("path") String path) throws IOException {
		return ResourceUtils.resolve(root, path) != null;
	}
	
	@Path("/resource/{path : .+}")
	@GET
	public Part read(@PathParam("path") String path) throws IOException {
		Resource resolved = ResourceUtils.resolve(root, path);
		if (resolved == null) {
			throw new HTTPException(404, "Could not find: " + path);
		}
		else if (!(resolved instanceof ReadableResource)) {
			throw new HTTPException(400, "The requested resource is not readable");
		}
		if (!(resolved instanceof FiniteResource)) {
			throw new HTTPException(500, "Can not establish the size of the requested resource");
		}
		ReadableContainer<ByteBuffer> content = new ResourceReadableContainer((ReadableResource) resolved);
		return new PlainMimeContentPart(null, content, 
			new MimeHeader("Content-Length", new Long(((FiniteResource) resolved).getSize()).toString()),
			new MimeHeader("Content-Type", resolved.getContentType() == null ? "application/octet-stream" : resolved.getContentType()),
			new MimeHeader("Content-Disposition", "attachment;filename=" + resolved.getName()),
			new MimeHeader("Writable", Boolean.toString(resolved instanceof WritableResource))
		);
	}
	
	@Path("/resource/{path : .+}")
	@PUT
	public void write(@PathParam("path") String path, InputStream content) throws IOException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		Resource resolved = ResourceUtils.resolve(root, path);
		if (resolved == null) {
			int index = path.lastIndexOf('/');
			Resource parent;
			if (index < 0) {
				parent = root;
			}
			else {
				parent = ResourceUtils.resolve(root, path.substring(0, index));
				if (parent == null) {
					parent = ResourceUtils.mkdirs(root, path.substring(0, index));
				}
			}
			if (!(parent instanceof ManageableContainer)) {
				throw new IOException("The parent " + parent + " is not manageable");
			}
			String contentType = URLConnection.guessContentTypeFromName(path);
			if (contentType == null) {
				contentType = "application/octet-stream";
			}
			resolved = ((ManageableContainer<?>) parent).create(path.substring(index + 1), contentType);
		}
		if (!(resolved instanceof WritableResource)) {
			throw new IOException("Could not find or create resource: " + path);
		}
		WritableContainer<ByteBuffer> writable = ((WritableResource) resolved).getWritable();
		try {
			if (content != null) {
				IOUtils.copyBytes(IOUtils.wrap(content), writable);
			}
		}
		finally {
			writable.close();
		}
	}
	
	@Path("/resource")
	@POST
	public void createInRoot(@QueryParam("name") String name, @QueryParam("type") String contentType) throws IOException {
		create("/", name, contentType);
	}
	
	@Path("/resource/{path : .+}")
	@POST
	public void create(@PathParam("path") String path, @QueryParam("name") String name, @QueryParam("type") String contentType) throws IOException {
		Resource resolved = path == null || path.isEmpty() || path.equals("/") ? root : ResourceUtils.resolve(root, path);
		if (resolved == null) {
			throw new RuntimeException("Can not find parent");
		}
		((ManageableContainer<?>) resolved).create(name, contentType);
	}
	
	@Path("/resource/{path : .+}")
	@DELETE
	public void delete(@PathParam("path") String path) throws IOException {
		Resource resolved = ResourceUtils.resolve(root, path);
		if (resolved != null) {
			ResourceContainer<?> parent = resolved.getParent();
			if (!(parent instanceof ManageableContainer)) {
				throw new IOException("The parent of '" + path + "' is not manageable");
			}
			((ManageableContainer<?>) parent).delete(resolved.getName());
		}
	}

	@Path("/list")
	@GET
	public Listing listRoot(@QueryParam("recursive") String recursive, @QueryParam("full") String full) throws IOException {
		return list("/", recursive, full);
	}

	@Path("/list/")
	@GET
	public Listing listRoot2(@QueryParam("recursive") String recursive, @QueryParam("full") String full) throws IOException {
		return list("/", recursive, full);
	}
	
	// let's do this with a third of a meg since we now have a client side caching mechanism
	private Long maxPreloadSize = 1024l*1024 / 3;
	
	@Path("/list/{path : .*}")
	@GET
	public Listing list(@PathParam("path") String path, @QueryParam("recursive") String recursive, @QueryParam("full") String full) throws IOException {
		if (path != null && path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path == null || path.isEmpty()) {
			path = "/";
		}
		Resource resolved = path.equals("/") ? root : ResourceUtils.resolve(root, path);
		if (resolved == null) {
			throw new HTTPException(404, "The resource does not exist: " + path);
		}
		// a directory, we need to provide a listing
		else if (!(resolved instanceof ResourceContainer)) {
			throw new HTTPException(400, "The requested resource is not listable: " + path);
		}
		Listing listing = new Listing();
		listing.setPath(path);
		listing.setManageable(resolved instanceof ManageableContainer);
		for (Resource child : (ResourceContainer<?>) resolved) {
			if (child.getName().startsWith(".")) {
				continue;
			}
			Entry entry = new Entry();
			entry.setPath(path + (path.equals("/") ? "" : "/") + child.getName());
			entry.setContentType(child.getContentType() == null ? "application/octet-stream" : child.getContentType());
			entry.setName(child.getName());
			entry.setWritable(child instanceof WritableResource || child instanceof ManageableContainer);
			if (child instanceof FiniteResource) {
				entry.setSize(((FiniteResource) child).getSize());
			}
			if (child instanceof TimestampedResource) {
				entry.setLastModified(((TimestampedResource) child).getLastModified());
			}
			if ("true".equals(full) && child instanceof ReadableResource && entry.getSize() != null && entry.getSize() < maxPreloadSize) {
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
				try {
					entry.setContent(IOUtils.toBytes(readable));
				}
				finally {
					readable.close();
				}
			}
			// if too big to include, add a hash so we can cache it at the other end
			else if ("true".equals(full) && child instanceof ReadableResource && entry.getSize() != null && entry.getSize() >= maxPreloadSize) {
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
				try {
					byte[] digest = SecurityUtils.digest(IOUtils.toInputStream(readable), DigestAlgorithm.MD5);
					entry.setHash(SecurityUtils.encodeDigest(digest));
				}
				catch (NoSuchAlgorithmException e) {
					// impossible?
					e.printStackTrace();
				}
				finally {
					readable.close();
				}
			}
			if ("true".equals(recursive) && child instanceof ResourceContainer) {
				entry.setChildren(list(entry.getPath(), recursive, full));
			}
			listing.getEntries().add(entry);
		}
		return listing;
	}
}

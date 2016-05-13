package be.nabu.libs.resources.remote.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

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

@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public class ResourceREST {
	
	@Context
	private ResourceContainer<?> root;
	
	@Path("/resource/{path : .+}")
	@GET
	public Part read(@PathParam("path") String path) throws IOException {
		Resource resolved = ResourceUtils.resolve(root, path);
		if (resolved == null) {
			throw new HTTPException(404);
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
			new MimeHeader("Content-Disposition", "attachment;filename=" + resolved.getName())
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
			IOUtils.copyBytes(IOUtils.wrap(content), writable);
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
	public Listing listRoot(@QueryParam("recursive") String recursive) throws IOException {
		return list("/", recursive);
	}
	
	@Path("/list/{path : .*}")
	@GET
	public Listing list(@PathParam("path") String path, @QueryParam("recursive") String recursive) throws IOException {
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
			throw new HTTPException(400, "The requested resource is not listable");
		}
		Listing listing = new Listing();
		listing.setPath(path);
		for (Resource child : (ResourceContainer<?>) resolved) {
			if (child.getName().startsWith(".")) {
				continue;
			}
			Entry entry = new Entry();
			entry.setPath(path + (path.equals("/") ? "" : "/") + child.getName());
			entry.setContentType(child.getContentType() == null ? "application/octet-stream" : child.getContentType());
			entry.setName(child.getName());
			if (child instanceof FiniteResource) {
				entry.setSize(((FiniteResource) child).getSize());
			}
			if (child instanceof TimestampedResource) {
				entry.setLastModified(((TimestampedResource) child).getLastModified());
			}
			if ("true".equals(recursive) && child instanceof ResourceContainer) {
				entry.setChildren(list(entry.getPath(), recursive));
			}
			listing.getEntries().add(entry);
		}
		return listing;
	}
}

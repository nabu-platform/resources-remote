package be.nabu.libs.resources.remote.client;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.features.CacheableResource;
import be.nabu.libs.resources.remote.server.Entry;
import be.nabu.libs.resources.remote.server.Listing;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.impl.FormatException;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteContainer extends RemoteResource implements ManageableContainer<RemoteResource>, CacheableResource {

	private boolean cache;
	private Map<String, RemoteResource> children;
	private boolean recursiveList;

	RemoteContainer(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, boolean recursiveList) {
		super(parent, itemName, contentType, lastModified, path);
		this.recursiveList = recursiveList;
	}

	public RemoteContainer(ConnectionHandler handler, String host, int port, String root, String username, String password, String itemName, String contentType, Date lastModified, String path, boolean recursiveList) {
		super(handler, host, port, root, username, password, itemName, contentType, lastModified, path);
		this.recursiveList = recursiveList;
	}

	private Map<String, RemoteResource> getChildren() {
		if (children == null) {
			synchronized(this) {
				if (children == null) {
					try {
						HTTPResponse response = getClient().execute(new DefaultHTTPRequest("GET", getRoot() + "list" + URIUtils.encodeURI(getPath()) + "?recursive=" + recursiveList, new PlainMimeEmptyPart(null, 
							new MimeHeader("Content-Length", "0"),
							new MimeHeader("Accept-Encoding", "gzip"),
							getHostHeader()
						)), getPrincipal(), isSecure(), false);
						
						if (response.getCode() >= 200 && response.getCode() < 300 && response.getContent() instanceof ContentPart) {
							Listing listing = TypeUtils.getAsBean(getBinding().unmarshal(IOUtils.toInputStream(((ContentPart) response.getContent()).getReadable()), new Window[0]), Listing.class);
							loadListing(listing);
						}
						else {
							throw new RuntimeException("Invalid response code " + response.getCode() + ": " + response.getMessage());
						}
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}
					catch (FormatException e) {
						throw new RuntimeException(e);
					}
					catch (ParseException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return children;
	}

	private synchronized void loadListing(Listing listing) {
		Map<String, RemoteResource> children = new HashMap<String, RemoteResource>();
		if (listing != null && listing.getEntries() != null) {
			for (Entry entry : listing.getEntries()) {
				if (Resource.CONTENT_TYPE_DIRECTORY.equals(entry.getContentType())) {
					RemoteContainer container = new RemoteContainer(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), recursiveList);
					children.put(entry.getName(), container);
					if (recursiveList) {
						container.loadListing(entry.getChildren());
					}
				}
				else {
					children.put(entry.getName(), new RemoteItem(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), entry.getSize()));
				}
			}
		}
		this.children = children;
	}
	
	@Override
	public Iterator<RemoteResource> iterator() {
		return getChildren().values().iterator();
	}

	@Override
	public RemoteResource getChild(String name) {
		return getChildren().get(name);
	}

	@Override
	public RemoteResource create(String name, String contentType) throws IOException {
		if (getChildren().containsKey(name)) {
			throw new IOException("A child by the name of '" + name + "' already exists");
		}
		String childPath = getPath() == null || getPath().isEmpty() || getPath().equals("/") ? name : getPath() + "/" + name;
		try {
			String encodeURI = URIUtils.encodeURI(getPath());
			if ("/".equals(encodeURI)) {
				encodeURI = "";
			}
			HTTPResponse response = getClient().execute(new DefaultHTTPRequest("POST", getRoot() + "resource" + encodeURI + "?type=" + contentType + "&name=" + URIUtils.encodeURIComponent(name), new PlainMimeEmptyPart(null, 
				new MimeHeader("Content-Length", "0"),
				getHostHeader()
			)), getPrincipal(), isSecure(), false);
			if (response.getCode() < 200 || response.getCode() >= 300) {
				throw new RuntimeException("Invalid response code " + response.getCode() + ": " + response.getMessage());
			}
			// the actual backend resource will be created upon use, so just send back a resource instance
			if (Resource.CONTENT_TYPE_DIRECTORY.equals(contentType)) {
				getChildren().put(name, new RemoteContainer(this, name, contentType, new Date(), childPath, recursiveList));
			}
			else {
				getChildren().put(name, new RemoteItem(this, name, contentType, new Date(), childPath, 0l));
			}
			return getChildren().get(name);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void delete(String name) throws IOException {
		try {
			if (getChildren().containsKey(name)) {
				HTTPResponse response = getClient().execute(new DefaultHTTPRequest("DELETE", getRoot() + "resource" + URIUtils.encodeURI(getPath() + "/" + name), new PlainMimeEmptyPart(null, 
					new MimeHeader("Content-Length", "0"),
					getHostHeader()
				)), getPrincipal(), isSecure(), false);
				if (response.getCode() < 200 || response.getCode() >= 300) {
					throw new IOException("Could not delete: " + name);
				}
				getChildren().remove(name);
			}
		}
		catch (FormatException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void resetCache() throws IOException {
		children = null;
	}

	@Override
	public void setCaching(boolean cache) {
		this.cache = cache;
	}

	@Override
	public boolean isCaching() {
		return cache;
	}

	public boolean isRecursiveList() {
		return recursiveList;
	}

	public void setRecursiveList(boolean recursiveList) {
		this.recursiveList = recursiveList;
	}
}

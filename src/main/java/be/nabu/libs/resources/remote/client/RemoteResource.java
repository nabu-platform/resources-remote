package be.nabu.libs.resources.remote.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Date;

import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.client.DefaultHTTPClient;
import be.nabu.libs.http.client.SPIAuthenticationHandler;
import be.nabu.libs.http.core.CustomCookieStore;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.LocatableResource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.api.principals.BasicPrincipal;
import be.nabu.libs.resources.remote.server.Listing;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;

public class RemoteResource implements TimestampedResource, Closeable, LocatableResource {
	
	private String itemName;
	private String contentType;
	private Date lastModified;
	private String path;
	private String host;
	private int port;
	private String username;
	private String password;
	private RemoteContainer parent;
	private HTTPClient client;
	private ConnectionHandler connectionHandler;
	private String root;
	private UnmarshallableBinding binding;

	RemoteResource(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path) {
		this.parent = parent;
		this.itemName = itemName;
		this.contentType = contentType;
		this.lastModified = lastModified;
		this.path = path;
		if (!this.path.startsWith("/")) {
			this.path = "/" + this.path;
		}
	}
	
	public RemoteResource(ConnectionHandler connectionHandler, String host, Integer port, String root, String username, String password, String itemName, String contentType, Date lastModified, String path) {
		this.connectionHandler = connectionHandler;
		this.root = root == null ? "/" : root;
		if (!this.root.endsWith("/")) {
			this.root += "/";
		}
		this.client = new DefaultHTTPClient(connectionHandler, new SPIAuthenticationHandler(), new CookieManager(new CustomCookieStore(), CookiePolicy.ACCEPT_NONE), false);
		this.host = host;
		this.port = port;
		if (port == null) {
			port = connectionHandler.getSecureContext() == null ? 80 : 443;
		}
		this.username = username;
		this.password = password;
		this.itemName = itemName;
		this.contentType = contentType;
		this.lastModified = lastModified;
		this.path = path;
		if (!this.path.startsWith("/")) {
			this.path = "/" + this.path;
		}
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public String getName() {
		return itemName;
	}

	@Override
	public ResourceContainer<?> getParent() {
		return parent;
	}
	
	protected Principal getPrincipal() {
		if (parent != null) {
			return parent.getPrincipal();
		}
		else {
			return username == null ? null : new BasicPrincipal() {
				@Override
				public String getName() {
					return username;
				}
				@Override
				public String getPassword() {
					return password;
				}
			};
		}
	}
	
	protected boolean isSecure() {
		return getConnectionHandler().getSecureContext() != null;
	}
	
	protected Header getHostHeader() {
		return parent == null 
			? new MimeHeader("Host", host + ":" + port)
			: parent.getHostHeader();
	}
	
	protected String getRoot() {
		return parent == null ? root : parent.getRoot();
	}

	protected String getItemName() {
		return itemName;
	}

	@Override
	public Date getLastModified() {
		return lastModified;
	}

	protected String getPath() {
		return path;
	}

	protected int getPort() {
		return parent == null ? port : parent.getPort();
	}

	protected String getUsername() {
		return parent == null ? username : parent.getUsername();
	}

	protected String getPassword() {
		return parent == null ? password : parent.getPassword();
	}

	protected HTTPClient getClient() {
		return parent == null ? client : parent.getClient();
	}

	protected ConnectionHandler getConnectionHandler() {
		return parent == null ? connectionHandler : parent.getConnectionHandler();
	}
	
	protected UnmarshallableBinding getBinding() {
		if (parent != null) {
			return parent.getBinding();
		}
		else if (binding == null) {
			binding = getParent() == null ? new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(Listing.class), Charset.forName("UTF-8")) : parent.getBinding();
		}
		return binding;
	}

	@Override
	public void close() throws IOException {
		if (parent == null) {
			connectionHandler.close();
		}
	}

	@Override
	public URI getURI() {
		if (parent != null) {
			return URIUtils.getChild(parent.getURI(), getName());
		}
		else {
			try {
				return new URI("remote://" + host + ":" + port + "/" + path);
			}
			catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	@Override
	public String toString() {
		return getURI().toString();
	}
}

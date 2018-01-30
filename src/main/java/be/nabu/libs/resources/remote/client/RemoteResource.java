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
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.client.DefaultHTTPClient;
import be.nabu.libs.http.client.SPIAuthenticationHandler;
import be.nabu.libs.http.core.CustomCookieStore;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.LocatableResource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.remote.server.Listing;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.UnmarshallableBinding;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteResource implements TimestampedResource, Closeable, LocatableResource {
	
	private String itemName;
	private String contentType;
	private Date lastModified;
	private String path;
	private String host;
	private int port;
	private RemoteContainer parent;
	private HTTPClient client;
	private ConnectionHandler connectionHandler;
	private String root;
	private UnmarshallableBinding binding;
	private boolean isSecure;
	private Executor executor;
	Logger logger = LoggerFactory.getLogger(getClass());
	private Principal principal;

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
	
	public RemoteResource(ConnectionHandler connectionHandler, String host, Integer port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path) {
		this.connectionHandler = connectionHandler;
		this.principal = principal;
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
		this.itemName = itemName;
		this.contentType = contentType;
		this.lastModified = lastModified;
		this.path = path;
		if (!this.path.startsWith("/")) {
			this.path = "/" + this.path;
		}
		this.isSecure = connectionHandler.getSecureContext() != null;
	}
	
	public RemoteResource(HTTPClient client, String host, Integer port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path, boolean isSecure) {
		this.principal = principal;
		this.isSecure = isSecure;
		this.root = root == null ? "/" : root;
		if (!this.root.endsWith("/")) {
			this.root += "/";
		}
		this.client = client;
		this.host = host;
		this.port = port == null ? 80 : port;
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
			return principal;
		}
	}
	
	protected boolean isSecure() {
		return parent == null ? isSecure : parent.isSecure();
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
			if (connectionHandler != null) {
				connectionHandler.close();
			}
			else if (client instanceof Closeable) {
				((Closeable) client).close();
			}
		}
	}

	@Override
	public URI getUri() {
		if (parent != null) {
			return URIUtils.getChild(parent.getUri(), getName());
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
		return getUri().toString();
	}
	
	public boolean exists() {
		try {
			HTTPResponse response = getClient().execute(new DefaultHTTPRequest("GET", getRoot() + "exists" + (getPath() == "/" ? "" : URIUtils.encodeURI(getPath())), new PlainMimeEmptyPart(null, 
				new MimeHeader("Content-Length", "0"),
				new MimeHeader("Accept-Encoding", "gzip"),
				getHostHeader()
			)), getPrincipal(), isSecure(), false);
			if (response.getCode() >= 200 && response.getCode() < 300 && response.getContent() instanceof ContentPart) {
				byte[] bytes = IOUtils.toBytes(((ContentPart) response.getContent()).getReadable());
				return !new String(bytes).equals("false");
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return false;
	}

	public Executor getExecutor() {
		return executor != null ? executor : (parent == null ? null : parent.getExecutor());
	}

	public void setExecutor(Executor executor) {
		this.executor = executor;
	}
	
}

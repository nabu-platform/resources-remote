package be.nabu.libs.resources.remote.client;

import java.io.IOException;
import java.security.Principal;
import java.text.ParseException;
import java.util.Date;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.features.CacheableResource;
import be.nabu.utils.mime.impl.FormatException;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteManageableContainer extends RemoteContainer implements ManageableContainer<RemoteResource>, CacheableResource {

	RemoteManageableContainer(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, boolean recursiveList) {
		super(parent, itemName, contentType, lastModified, path, recursiveList);
	}

	public RemoteManageableContainer(ConnectionHandler handler, String host, int port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path, boolean recursiveList, boolean fullList) {
		super(handler, host, port, root, principal, itemName, contentType, lastModified, path, recursiveList, fullList);
	}

	public RemoteManageableContainer(HTTPClient client, String host, int port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path, boolean isSecure, boolean recursiveList, boolean fullList) {
		super(client, host, port, root, principal, itemName, contentType, lastModified, path, isSecure, recursiveList, fullList);
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
				getChildren().put(name, new RemoteManageableContainer(this, name, contentType, new Date(), childPath, recursiveList));
			}
			else {
				getChildren().put(name, new RemoteWritableItem(this, name, contentType, new Date(), childPath, 0l, new byte[0], null));
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


}

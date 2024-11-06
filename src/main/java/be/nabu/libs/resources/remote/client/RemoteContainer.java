/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.resources.remote.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.api.client.HTTPClient;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.features.CacheableResource;
import be.nabu.libs.resources.remote.server.Entry;
import be.nabu.libs.resources.remote.server.Listing;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteContainer extends RemoteResource implements ResourceContainer<RemoteResource>, CacheableResource {
	private boolean cache;
	private Map<String, RemoteResource> children;
	protected boolean recursiveList, fullList;

	RemoteContainer(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, boolean recursiveList) {
		super(parent, itemName, contentType, lastModified, path);
		this.recursiveList = recursiveList;
	}

	public RemoteContainer(ConnectionHandler handler, String host, int port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path, boolean recursiveList, boolean fullList) {
		super(handler, host, port, root, principal, itemName, contentType, lastModified, path);
		this.recursiveList = recursiveList;
		this.fullList = fullList;
	}
	
	public RemoteContainer(HTTPClient client, String host, int port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path, boolean isSecure, boolean recursiveList, boolean fullList) {
		super(client, host, port, root, principal, itemName, contentType, lastModified, path, isSecure);
		this.recursiveList = recursiveList;
		this.fullList = fullList;
	}
	
	protected Map<String, RemoteResource> getChildren() {
		if (children == null) {
			synchronized(this) {
				if (children == null) {
					HTTPResponse response = null;
					try {
						response = getClient().execute(new DefaultHTTPRequest("GET", getRoot() + "list" + URIUtils.encodeURI(getPath()) + "?recursive=" + recursiveList + "&full=" + fullList, new PlainMimeEmptyPart(null, 
							new MimeHeader("Content-Length", "0"),
							new MimeHeader("Accept-Encoding", "gzip"),
							getHostHeader()
						)), getPrincipal(), isSecure(), false);
						
						if (response.getCode() >= 200 && response.getCode() < 300 && response.getContent() instanceof ContentPart) {
							
							byte [] content = IOUtils.toBytes(((ContentPart) response.getContent()).getReadable());
							Listing listing = TypeUtils.getAsBean(getBinding().unmarshal(new ByteArrayInputStream(content), new Window[0]), Listing.class);
							loadListing(listing);
						}
						else {
							throw new RuntimeException("Invalid response code " + response.getCode() + ": " + response.getMessage());
						}
					}
					catch (Exception e) {
						throw new RuntimeException("Can not read: " + getRoot() + "list" + URIUtils.encodeURI(getPath()) + "?recursive=" + recursiveList + "&full=" + fullList, e);
					}
				}
			}
		}
		return children;
	}

	private void loadListing(Listing listing) {
		Map<String, RemoteResource> children = new HashMap<String, RemoteResource>();
		if (listing != null && listing.getEntries() != null) {
			for (Entry entry : listing.getEntries()) {
				if (Resource.CONTENT_TYPE_DIRECTORY.equals(entry.getContentType())) {
					RemoteContainer container = entry.isWritable() 
						? new RemoteManageableContainer(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), recursiveList)
						: new RemoteContainer(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), recursiveList);
					children.put(entry.getName(), container);
					if (recursiveList) {
						container.loadListing(entry.getChildren());
					}
				}
				else {
					children.put(entry.getName(), entry.isWritable()
						? new RemoteWritableItem(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), entry.getSize(), entry.getContent(), entry.getHash())
						: new RemoteItem(this, entry.getName(), entry.getContentType(), entry.getLastModified(), entry.getPath(), entry.getSize(), entry.getContent(), entry.getHash()));
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
	public void resetCache() throws IOException {
		// only reset the children if we are not using the executor
		if (getExecutor() == null) {
			children = null;
		}
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

	public boolean isFullList() {
		return fullList;
	}

	public void setFullList(boolean fullList) {
		this.fullList = fullList;
	}
	

}

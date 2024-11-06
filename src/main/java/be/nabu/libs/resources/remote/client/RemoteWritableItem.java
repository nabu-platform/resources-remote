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

import java.io.IOException;
import java.security.Principal;
import java.util.Date;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeContentPart;

public class RemoteWritableItem extends RemoteItem implements WritableResource {

	public RemoteWritableItem(ConnectionHandler connectionHandler, String host, Integer port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path) {
		super(connectionHandler, host, port, root, principal, itemName, contentType, lastModified, path);
	}
	
	RemoteWritableItem(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, Long size, byte [] content, String hash) {
		super(parent, itemName, contentType, lastModified, path, size, content, hash);
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws IOException {
		return new WritableContainer<ByteBuffer>() {
			private boolean closed;
			private ByteBuffer buffer = ByteBufferFactory.getInstance().newInstance();
			@Override
			public void close() throws IOException {
				// only close once, otherwise we may have buffer issues
				// we had a case where two writes happened, one with content then one without
				if (!closed) {
					closed = true;
					buffer.close();
					try {
						final byte [] content = IOUtils.toBytes(buffer);
						Runnable action = new Runnable() {
							public void run() {
								try {
									HTTPResponse response = getClient().execute(new DefaultHTTPRequest("PUT", getRoot() + "resource" + URIUtils.encodeURI(getPath()), new PlainMimeContentPart(null, 
										IOUtils.wrap(content, true),
										new MimeHeader("Content-Type", getContentType()),
										new MimeHeader("Content-Length", Long.toString(content.length)),
										getHostHeader()
									)), getPrincipal(), isSecure(), false);
									if (response.getCode() < 200 || response.getCode() >= 300) {
										throw new IOException("Could not persist data: " + response.getCode() + " - " + response.getMessage());
									}
									else {
										RemoteWritableItem.this.size = (long) content.length;
										RemoteWritableItem.this.content = content;
										cache(content);
									}
								}
								catch (Exception e) {
									logger.error("Could not persist data for: " + getUri(), e);
									throw new RuntimeException(e);
								}
							}
						};
						if (getExecutor() == null) {
							action.run();
						}
						else {
							// already update locally, assuming everything will (eventually) be persisted
							RemoteWritableItem.this.size = (long) content.length;
							RemoteWritableItem.this.content = content;
							getExecutor().execute(action);
							cache(content);
						}
					}
					catch (IOException e) {
						throw e;
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
			@Override
			public long write(ByteBuffer buffer) throws IOException {
				return this.buffer.write(buffer);
			}
			@Override
			public void flush() throws IOException {
				this.buffer.flush();
			}
		};
	}

}

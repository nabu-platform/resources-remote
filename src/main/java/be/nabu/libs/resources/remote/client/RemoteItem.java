package be.nabu.libs.resources.remote.client;

import java.io.IOException;
import java.util.Date;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteItem extends RemoteResource implements ReadableResource, WritableResource, FiniteResource {

	private Long size;
	private byte[] content;

	public RemoteItem(ConnectionHandler connectionHandler, String host, Integer port, String root, String username, String password, String itemName, String contentType, Date lastModified, String path) {
		super(connectionHandler, host, port, root, username, password, itemName, contentType, lastModified, path);
	}
	
	RemoteItem(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, Long size) {
		super(parent, itemName, contentType, lastModified, path);
		this.size = size;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws IOException {
		return new WritableContainer<ByteBuffer>() {
			private ByteBuffer buffer = ByteBufferFactory.getInstance().newInstance();
			@Override
			public void close() throws IOException {
				buffer.close();
				try {
					byte [] content = IOUtils.toBytes(buffer);
					HTTPResponse response = getClient().execute(new DefaultHTTPRequest("PUT", getRoot() + "resource" + URIUtils.encodeURI(getPath()), new PlainMimeContentPart(null, 
						IOUtils.wrap(content, true),
						new MimeHeader("Content-Type", getContentType()),
						new MimeHeader("Content-Length", Long.toString(content.length)),
						getHostHeader()
					)), getPrincipal(), isSecure(), false);
					if (response.getCode() != 200) {
						throw new IOException("Could not persist data: " + response.getCode() + " - " + response.getMessage());
					}
					else {
						RemoteItem.this.size = (long) content.length;
						RemoteItem.this.content = content;
					}
				}
				catch (IOException e) {
					throw e;
				}
				catch (Exception e) {
					throw new RuntimeException(e);
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

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		if (content == null) {
			try {
				HTTPResponse response = getClient().execute(new DefaultHTTPRequest("GET", getRoot() + "resource" + URIUtils.encodeURI(getPath()), new PlainMimeEmptyPart(null, 
					new MimeHeader("Content-Length", "0"),
					new MimeHeader("Accept-Encoding", "gzip"),
					getHostHeader()
				)), getPrincipal(), isSecure(), false);
				if (response.getCode() != 200 || !(response.getContent() instanceof ContentPart)) {
					throw new IOException("Could not get content: " + response.getCode() + " - " + response.getMessage());
				}
				content = IOUtils.toBytes(((ContentPart) response.getContent()).getReadable());
			}
			catch (IOException e) {
				throw e;
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return IOUtils.wrap(content, true);
	}

}

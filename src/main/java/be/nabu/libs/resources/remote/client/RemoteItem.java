package be.nabu.libs.resources.remote.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Date;

import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.client.ConnectionHandler;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.resources.api.features.CacheableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeContentPart;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class RemoteItem extends RemoteResource implements ReadableResource, WritableResource, FiniteResource, CacheableResource {

	private Long size;
	private byte[] content;
	private String hash;

	public RemoteItem(ConnectionHandler connectionHandler, String host, Integer port, String root, Principal principal, String itemName, String contentType, Date lastModified, String path) {
		super(connectionHandler, host, port, root, principal, itemName, contentType, lastModified, path);
	}
	
	RemoteItem(RemoteContainer parent, String itemName, String contentType, Date lastModified, String path, Long size, byte [] content, String hash) {
		super(parent, itemName, contentType, lastModified, path);
		this.size = size;
		this.content = content;
		this.hash = hash;
	}

	@Override
	public long getSize() {
		return size == null ? 0 : size;
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws IOException {
		return new WritableContainer<ByteBuffer>() {
			private ByteBuffer buffer = ByteBufferFactory.getInstance().newInstance();
			@Override
			public void close() throws IOException {
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
									RemoteItem.this.size = (long) content.length;
									RemoteItem.this.content = content;
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
						RemoteItem.this.size = (long) content.length;
						RemoteItem.this.content = content;
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
			// if we have a cache location and a hash, check if we cached it
			if (cacheLocation != null && hash != null) {
				File file = new File(cacheLocation, hash);
				if (file.exists() && file.isFile()) {
					InputStream input = new BufferedInputStream(new FileInputStream(file));
					try {
						content = IOUtils.toBytes(IOUtils.wrap(input));
					}
					finally {
						input.close();
					}
				}
			}
			if (content == null) {
				try {
					HTTPResponse response = getClient().execute(new DefaultHTTPRequest("GET", getRoot() + "resource" + URIUtils.encodeURI(getPath()), new PlainMimeEmptyPart(null, 
						new MimeHeader("Content-Length", "0"),
						new MimeHeader("Accept-Encoding", "gzip"),
						getHostHeader()
					)), getPrincipal(), isSecure(), false);
					if (response.getCode() < 200 || response.getCode() >= 300 || !(response.getContent() instanceof ContentPart)) {
						throw new IOException("Could not get content: " + response.getCode() + " - " + response.getMessage());
					}
					ReadableContainer<ByteBuffer> readable = response == null || response.getContent() == null ? null : ((ContentPart) response.getContent()).getReadable();
					if (readable == null) {
						content = new byte[0];
						cache(content);
					}
					else {
						content = IOUtils.toBytes(readable);
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
		return IOUtils.wrap(content, true);
	}

	private void cache(byte [] content) throws FileNotFoundException, IOException {
		// store it for later reuse
		if (cacheLocation != null && hash != null) {
			File cache = new File(cacheLocation);
			if (!cache.exists()) {
				cache.mkdirs();
			}
			File file = new File(cache, hash);
			if (content == null || content.length == 0) {
				file.delete();
			}
			else {
				OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
				try {
					output.write(content);
				}
				finally {
					output.close();
				}
			}
		}
	}

	@Override
	public void resetCache() throws IOException {
		content = null;
		size = 0l;
	}

	@Override
	public void setCaching(boolean cache) {
		// do nothing
	}

	@Override
	public boolean isCaching() {
		return true;
	}

}

package be.nabu.libs.resources.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.impl.EventDispatcherImpl;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.libs.http.server.rest.RESTHandler;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.remote.server.ResourceREST;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.impl.MimeHeader;

public class TestServer {
	public static void main(String...args) throws IOException, URISyntaxException {
		Resource root = ResourceFactory.getInstance().resolve(new URI("file:/home/alex/tmp"), null);
		final HTTPServer server = HTTPServerUtils.newServer(7890, 20, new EventDispatcherImpl());
		server.getDispatcher().subscribe(HTTPRequest.class, new RESTHandler("/", ResourceREST.class, null, root));
		server.getDispatcher().subscribe(HTTPResponse.class, new EventHandler<HTTPResponse, HTTPResponse>() {
			@Override
			public HTTPResponse handle(HTTPResponse response) {
//				if (response.getContent() != null) {
//					response.getContent().setHeader(new MimeHeader("Content-Encoding", "gzip"));
//				}
				return null;
			}
		});
		// start the server
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					server.start();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
//		thread.setDaemon(true);
		thread.start();
		
//		// let's create a client on it
//		Resource resolve = ResourceFactory.getInstance().resolve(new URI("remote://localhost:7890/"), null);
//		System.out.println(resolve);
//		for (Resource child : (ResourceContainer<?>) resolve) {
//			if (child instanceof ReadableResource) {
//				ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
//				try {
//					System.out.println(new String(IOUtils.toBytes(readable)));
//				}
//				finally {
//					readable.close();
//				}
//			}
//			System.out.println("Child: " + child);
//		}
//		ResourceUtils.close(resolve);
	}
}

package be.nabu.libs.resources.remote.client;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import be.nabu.libs.authentication.impl.BasicPrincipalImpl;
import be.nabu.libs.events.impl.EventDispatcherImpl;
import be.nabu.libs.http.client.connections.PlainConnectionHandler;
import be.nabu.libs.http.client.nio.NIOHTTPClientImpl;
import be.nabu.libs.http.core.CustomCookieStore;
import be.nabu.libs.http.server.nio.MemoryMessageDataProvider;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceResolver;

public class RemoteResourceResolver implements ResourceResolver {

	@Override
	public Resource getResource(URI uri, Principal principal) throws IOException {
		if (principal == null) {
			String username = null, password = null;
			if (uri.getUserInfo() != null) {
				username = uri.getUserInfo();
				int index = username.indexOf(':');
				if (index > 0) {
					password = username.substring(index + 1);
					username = username.substring(0, index);
				}
			}
			if (username != null) {
				principal = new BasicPrincipalImpl(username, password);
			}
		}
		Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
		boolean recursive = queryProperties.containsKey("recursive") ? Boolean.parseBoolean(queryProperties.get("recursive").get(0)) : Boolean.parseBoolean(System.getProperty("resources.remote.recursive", "true"));
		boolean full = queryProperties.containsKey("full") ? Boolean.parseBoolean(queryProperties.get("full").get(0)) : Boolean.parseBoolean(System.getProperty("resources.remote.full", "false"));
		List<String> timeout = queryProperties.get("timeout");
		try {
			RemoteContainer remoteContainer;
			if (Boolean.parseBoolean(System.getProperty("http.experimental.client", "true"))) {
				NIOHTTPClientImpl httpClient = new NIOHTTPClientImpl(uri.getScheme().equals("https") ? SSLContext.getDefault() : null, 5, 3, 10, new EventDispatcherImpl(), new MemoryMessageDataProvider(), new CookieManager(new CustomCookieStore(), CookiePolicy.ACCEPT_NONE), Executors.defaultThreadFactory());
				if (timeout != null && !timeout.isEmpty()) {
					httpClient.setRequestTimeout(Long.parseLong(timeout.get(0)));
				}
				remoteContainer = new RemoteContainer(
					httpClient,
					uri.getHost(), uri.getPort(), uri.getPath(), principal, null, Resource.CONTENT_TYPE_DIRECTORY, new Date(), "/", uri.getScheme().equals("https"), recursive, full
				);
			}
			else {
				remoteContainer = new RemoteContainer(new PlainConnectionHandler(uri.getScheme().equals("https") ? SSLContext.getDefault() : null, 10*1000*60, 10*1000*60), uri.getHost(), uri.getPort(), uri.getPath(), principal, null, Resource.CONTENT_TYPE_DIRECTORY, new Date(), "/", recursive, full);
			}
			return remoteContainer.exists() ? remoteContainer : null;
		}
		catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	@Override
	public List<String> getDefaultSchemes() {
		return Arrays.asList(new String [] { "remote" });
	}

}

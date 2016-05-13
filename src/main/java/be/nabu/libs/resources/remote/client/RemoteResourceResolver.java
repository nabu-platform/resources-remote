package be.nabu.libs.resources.remote.client;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import be.nabu.libs.http.client.connections.PooledConnectionHandler;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceResolver;
import be.nabu.libs.resources.api.principals.BasicPrincipal;

public class RemoteResourceResolver implements ResourceResolver {

	@Override
	public Resource getResource(URI uri, Principal principal) throws IOException {
		String username = null, password = null;
		if (uri.getUserInfo() != null) {
			username = uri.getUserInfo();
			int index = username.indexOf(':');
			if (index > 0) {
				password = username.substring(index + 1);
				username = username.substring(0, index);
			}
		}
		else if (principal instanceof BasicPrincipal) {
			username = principal.getName();
			password = ((BasicPrincipal) principal).getPassword();
		}
		boolean recursive = Boolean.parseBoolean(System.getProperty("resources.remote.recursive", "true"));
		return new RemoteContainer(new PooledConnectionHandler(null, 5), uri.getHost(), uri.getPort(), uri.getPath(), username, password, null, Resource.CONTENT_TYPE_DIRECTORY, new Date(), "/", recursive);
	}

	@Override
	public List<String> getDefaultSchemes() {
		return Arrays.asList(new String [] { "remote" });
	}

}

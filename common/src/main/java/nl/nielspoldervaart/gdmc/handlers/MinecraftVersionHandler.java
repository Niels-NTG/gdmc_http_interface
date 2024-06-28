package nl.nielspoldervaart.gdmc.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class MinecraftVersionHandler extends HandlerBase {
	public MinecraftVersionHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {
		String method = httpExchange.getRequestMethod().toLowerCase();

		if (!method.equals("get")) {
			throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);
		setResponseHeadersContentTypePlain(responseHeaders);

		String responseString = mcServer.getServerVersion();
		resolveRequest(httpExchange, responseString);
	}
}

package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class InterfaceInfoHandler extends HandlerBase {
	public InterfaceInfoHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {
		String method = httpExchange.getRequestMethod().toLowerCase();

		if (!method.equals("options")) {
			throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		JsonObject json = new JsonObject();
		json.addProperty("minecraftVersion", mcServer.getServerVersion());
		json.addProperty("interfaceVersion", "1.1.0");

		resolveRequest(httpExchange, json.toString());
	}
}

package com.gdmc.httpinterfacemod.handlers;

import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class InfoHandler extends HandlerBase {
	public InfoHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {
		String responseString = mcServer.getServerVersion();
		resolveRequest(httpExchange, responseString);
	}
}

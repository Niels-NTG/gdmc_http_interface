package org.ntg.gdmc.gdmchttpinterface.handlers;

import org.ntg.gdmc.gdmchttpinterface.GdmcHttpMod;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.JarVersionLookupHandler;

import java.io.IOException;
import java.util.Optional;

public class InterfaceInfoHandler extends HandlerBase {
	public InterfaceInfoHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {
		String method = httpExchange.getRequestMethod().toLowerCase();

		if (!method.equals("options")) {
			throw new HttpException("Method not allowed. Only OPTIONS requests are supported.", 405);
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		JsonObject json = new JsonObject();
		json.addProperty("minecraftVersion", mcServer.getServerVersion());
		Optional<String> modVersion = JarVersionLookupHandler.getImplementationVersion(GdmcHttpMod.class);
		if (modVersion.isPresent()) {
			json.addProperty("interfaceVersion", modVersion.get());
		}

		resolveRequest(httpExchange, json.toString());
	}
}

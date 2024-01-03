package nl.nielspoldervaart.gdmc.handlers;

import net.minecraft.SharedConstants;
import nl.nielspoldervaart.gdmc.GdmcHttpMod;
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
		json.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
		// Return DataVersion (https://minecraft.wiki/w/Data_version) of current Minecraft version.
		// Beware that the current server world might be created in an older version of Minecraft and
		// hence might have a different DataVersion.
		json.addProperty(SharedConstants.DATA_VERSION_TAG, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		Optional<String> modVersion = JarVersionLookupHandler.getImplementationVersion(GdmcHttpMod.class);
		if (modVersion.isPresent()) {
			json.addProperty("interfaceVersion", modVersion.get());
		}

		resolveRequest(httpExchange, json.toString());
	}
}

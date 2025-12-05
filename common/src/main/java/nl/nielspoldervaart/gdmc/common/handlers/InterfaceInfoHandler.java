package nl.nielspoldervaart.gdmc.common.handlers;

import net.minecraft.SharedConstants;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.utils.ModVersionRecord;

import java.io.IOException;

public class InterfaceInfoHandler extends HandlerBase {
	public InterfaceInfoHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {

		if (!httpExchange.getRequestMethod().equalsIgnoreCase("options")) {
			throw new HttpException("Method not allowed. Only OPTIONS requests are supported.", 405);
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		JsonObject json = new JsonObject();
		#if (MC_VER == MC_1_21_10)
		json.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
		#else
		json.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
		#endif
		// Return DataVersion (https://minecraft.wiki/w/Data_version) of current Minecraft version.
		// Beware that the current server world might be created in an older version of Minecraft and
		// hence might have a different DataVersion.
		#if (MC_VER == MC_1_21_10)
		json.addProperty(SharedConstants.DATA_VERSION_TAG, SharedConstants.getCurrentVersion().dataVersion().version());
		#else
		json.addProperty(SharedConstants.DATA_VERSION_TAG, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		#endif
		String modVersion = ModVersionRecord.getModVersion();
		if (modVersion != null) {
			json.addProperty("interfaceVersion", modVersion);
		}

		resolveRequest(httpExchange, json.toString());
	}
}

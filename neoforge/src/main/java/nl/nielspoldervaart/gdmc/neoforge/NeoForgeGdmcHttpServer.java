package nl.nielspoldervaart.gdmc.neoforge;

import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.GdmcHttpServer;
import nl.nielspoldervaart.gdmc.neoforge.config.GdmcHttpConfig;

import java.io.IOException;

public class NeoForgeGdmcHttpServer extends GdmcHttpServer {

	public static int getHttpServerPortConfig() {
		return GdmcHttpConfig.HTTP_INTERFACE_PORT.get();
	}

	public static void startServer(MinecraftServer minecraftServer) throws IOException {
		if (NeoForgeGdmcHttpServer.mcServer != minecraftServer) {
			NeoForgeGdmcHttpServer.mcServer = minecraftServer;
		}
		NeoForgeGdmcHttpServer.startServer(NeoForgeGdmcHttpServer.getHttpServerPortConfig());
	}

}

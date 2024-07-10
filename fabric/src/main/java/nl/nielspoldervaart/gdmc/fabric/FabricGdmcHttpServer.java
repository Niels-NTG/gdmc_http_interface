package nl.nielspoldervaart.gdmc.fabric;

import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.GdmcHttpServer;
import nl.nielspoldervaart.gdmc.fabric.config.GdmcHttpConfig;

import java.io.IOException;

public class FabricGdmcHttpServer extends GdmcHttpServer {

	public static int getHttpServerPortConfig() {
		return GdmcHttpConfig.HTTP_INTERFACE_PORT;
	}

	public static void startServer(MinecraftServer mcServer) throws IOException {
		if (FabricGdmcHttpServer.mcServer != mcServer) {
			FabricGdmcHttpServer.mcServer = mcServer;
		}
		FabricGdmcHttpServer.startServer(FabricGdmcHttpServer.getHttpServerPortConfig());
	}

}

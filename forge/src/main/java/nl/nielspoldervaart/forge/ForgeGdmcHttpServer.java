package nl.nielspoldervaart.forge;

import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.forge.config.GdmcHttpConfig;
import nl.nielspoldervaart.gdmc.GdmcHttpServer;

import java.io.IOException;

public final class ForgeGdmcHttpServer extends GdmcHttpServer {

    public static int getHttpServerPortConfig() {
        return GdmcHttpConfig.HTTP_INTERFACE_PORT.get();
    }

    public static void startServer(MinecraftServer mcServer) throws IOException {
        if (ForgeGdmcHttpServer.mcServer != mcServer) {
            ForgeGdmcHttpServer.mcServer = mcServer;
        }
        GdmcHttpServer.startServer(ForgeGdmcHttpServer.getHttpServerPortConfig());
    }
}

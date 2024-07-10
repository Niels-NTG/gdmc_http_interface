package nl.nielspoldervaart.gdmc.forge;

import nl.nielspoldervaart.gdmc.forge.config.GdmcHttpConfig;
import nl.nielspoldervaart.gdmc.common.GdmcHttpServer;

public final class ForgeGdmcHttpServer extends GdmcHttpServer {

    public static int getHttpServerPortConfig() {
        return GdmcHttpConfig.HTTP_INTERFACE_PORT.get();
    }

}

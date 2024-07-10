package nl.nielspoldervaart.gdmc.common;

import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.handlers.*;

import java.io.IOException;
import java.net.InetSocketAddress;

public class GdmcHttpServer {
    private static HttpServer httpServer;
    public static MinecraftServer mcServer;

    public static boolean hasHtppServerStarted = false;

    public static int getHttpServerPortConfig() {
        return 9000;
    }

    public static int getCurrentHttpPort() {
        return httpServer.getAddress().getPort();
    }

    public static void startServer(MinecraftServer mcServer) throws IOException {
        if (GdmcHttpServer.mcServer != mcServer) {
            GdmcHttpServer.mcServer = mcServer;
        }
        startServer(getHttpServerPortConfig());
    }

    public static void startServer(int portNumber) throws IOException {
        // Create HTTP server on localhost with the port number defined in the config file.
        httpServer = HttpServer.create(new InetSocketAddress(portNumber), 0);
        httpServer.setExecutor(null); // creates a default executor
        createContexts();
        httpServer.start();
        hasHtppServerStarted = true;
        // TODO create Queue on the HTTP server level when mcServer is busy so GDMC-HTTP
//          can keep receiving responses while mcServer is busy.
//          See what com.sun.net.httpserver.HttpServer can do for me here.
//          https://www.geeksforgeeks.org/queue-interface-java/
    }

    public static void stopServer() {
        if (httpServer != null) {
            httpServer.stop(5);
        }
        hasHtppServerStarted = false;
    }

    private static void createContexts() {
        httpServer.createContext("/command", new CommandHandler(mcServer));
        httpServer.createContext("/chunks", new ChunksHandler(mcServer));
        httpServer.createContext("/blocks", new BlocksHandler(mcServer));
        httpServer.createContext("/buildarea", new BuildAreaHandler(mcServer));
        httpServer.createContext("/version", new MinecraftVersionHandler(mcServer));
        httpServer.createContext("/biomes", new BiomesHandler(mcServer));
        httpServer.createContext("/structure", new StructureHandler(mcServer));
        httpServer.createContext("/entities", new EntitiesHandler(mcServer));
        httpServer.createContext("/players", new PlayersHandler(mcServer));
        httpServer.createContext("/heightmap", new HeightmapHandler(mcServer));
        httpServer.createContext("/", new InterfaceInfoHandler(mcServer));
    }
}

package com.gdmc.httpinterfacemod;

import com.gdmc.httpinterfacemod.handlers.*;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class GdmcHttpServer {
    private static HttpServer httpServer;
    private static MinecraftServer mcServer;

    public static void startServer(MinecraftServer mcServer) throws IOException {
        if (GdmcHttpServer.mcServer != mcServer) {
            GdmcHttpServer.mcServer = mcServer;
        }
        startServer(9000);
    }

    public static void startServer(int portNumber) throws IOException {


        // Stop server if one was already running
        stopServer();

        httpServer = HttpServer.create(new InetSocketAddress(portNumber), 0);
        httpServer.setExecutor(null); // creates a default executor
        createContexts();
        httpServer.start();
    }

    public static void stopServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private static void createContexts() {
        httpServer.createContext("/command", new CommandHandler(mcServer));
        httpServer.createContext("/chunks", new ChunkHandler(mcServer));
        httpServer.createContext("/blocks", new BlocksHandler(mcServer));
        httpServer.createContext("/buildarea", new BuildAreaHandler(mcServer));
        httpServer.createContext("/version", new MinecraftVersionHandler(mcServer));
        httpServer.createContext("/biomes", new BiomesHandler(mcServer));
        httpServer.createContext("/structure", new StructureHandler(mcServer));
        httpServer.createContext("/entities", new EntitiesHandler(mcServer));
        httpServer.createContext("/", new InterfaceInfoHandler(mcServer));
    }
}
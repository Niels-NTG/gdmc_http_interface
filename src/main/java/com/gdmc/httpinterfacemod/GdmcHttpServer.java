package com.gdmc.httpinterfacemod;

import com.gdmc.httpinterfacemod.config.GdmcHttpConfig;
import com.gdmc.httpinterfacemod.handlers.*;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class GdmcHttpServer {
    private static HttpServer httpServer;
    private static MinecraftServer mcServer;

    public static int getHttpServerPortConfig() {
        return GdmcHttpConfig.HTTP_INTERFACE_PORT.get();
    }
    public static void setHttpServerPortConfig(int portNumber) {
        GdmcHttpConfig.HTTP_INTERFACE_PORT.set(portNumber);
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
        // Stop server if one was already running
        stopServer();

        httpServer = HttpServer.create(new InetSocketAddress(portNumber), 0);
        httpServer.setExecutor(null); // creates a default executor
        createContexts();
        httpServer.start();

        // Update mod config file
        setHttpServerPortConfig(portNumber);
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
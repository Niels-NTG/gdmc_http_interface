package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class BuildAreaHandler extends HandlerBase {

    private static BuildArea buildArea;

    public BuildAreaHandler(MinecraftServer mcServer) {
        super(mcServer);
        unsetBuildArea();
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {
        String method = httpExchange.getRequestMethod().toLowerCase();
        if (!method.equals("get")) {
            throw new HttpException("Please use GET method to request the build area.", 405);
        }

        if (buildArea == null) {
            throw new HttpException("No build area is specified. Use the buildarea command inside Minecraft to set a build area.", 404);
        }

        String responseString = new Gson().toJson(buildArea);

        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, responseString);
    }

    public static void setBuildArea(int xFrom, int yFrom, int zFrom, int xTo, int yTo, int zTo) {
        buildArea = new BuildArea(xFrom, yFrom, zFrom, xTo, yTo, zTo);
    }

    public static void unsetBuildArea() {
        buildArea = null;
    }

    public static class BuildArea {
        private final int xFrom;
        private final int yFrom;
        private final int zFrom;
        private final int xTo;
        private final int yTo;
        private final int zTo;

        public BuildArea(int xFrom, int yFrom, int zFrom, int xTo, int yTo, int zTo) {
            this.xFrom = xFrom;
            this.yFrom = yFrom;
            this.zFrom = zFrom;
            this.xTo = xTo;
            this.yTo = yTo;
            this.zTo = zTo;
        }
    }
}
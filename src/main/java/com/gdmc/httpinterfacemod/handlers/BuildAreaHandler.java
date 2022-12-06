package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class BuildAreaHandler extends HandlerBase {
    public static class BuildArea {
        private final int xFrom;
        private final int yFrom;
        private final int zFrom;
        private final int xTo;
        private final int yTo;
        private final int zTo;

        public int getxFrom() { return xFrom; }
        public int getyFrom() { return yFrom; }
        public int getzFrom() { return zFrom; }
        public int getxTo() { return xTo; }
        public int getyTo() { return yTo; }
        public int getzTo() { return zTo; }

        public BuildArea(int xFrom, int yFrom, int zFrom, int xTo, int yTo, int zTo) {
            this.xFrom = xFrom;
            this.yFrom = yFrom;
            this.zFrom = zFrom;
            this.xTo = xTo;
            this.yTo = yTo;
            this.zTo = zTo;
        }

        @Override
        public String toString() {
            return "BuildArea{" +
                    "areaX=" + xFrom +
                    ", areaZ=" + zFrom +
                    ", areaSizeX=" + xTo +
                    ", areaSizeZ=" + zTo +
                    '}';
        }
    }

//    private static final Logger LOGGER = LogManager.getLogger();
    private static BuildArea buildArea;

    public static void setBuildArea(int xFrom, int yFrom, int zFrom, int xTo, int yTo, int zTo) {
        buildArea = new BuildArea(xFrom, yFrom, zFrom, xTo, yTo, zTo);
    }

    public BuildAreaHandler(MinecraftServer mcServer) {
        super(mcServer);
        buildArea = null;
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {
        // throw errors when appropriate
        String method = httpExchange.getRequestMethod().toLowerCase();
        if(!method.equals("get")) {
            throw new HandlerBase.HttpException("Please use GET method to request the build area.", 405);
        }

        if(buildArea == null) {
            throw new HandlerBase.HttpException("No build area is specified. Use the buildarea command inside Minecraft to set a build area.",404);
        }

        String responseString = new Gson().toJson(buildArea);

        Headers headers = httpExchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=UTF-8");

        resolveRequest(httpExchange, responseString);
    }
}
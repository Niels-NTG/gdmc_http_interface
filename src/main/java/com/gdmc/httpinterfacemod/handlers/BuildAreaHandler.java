package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

public class BuildAreaHandler extends HandlerBase {

    private static BuildArea buildArea;

    public static BuildArea getBuildArea() {
        return buildArea;
    }

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

    public static void setBuildArea(BlockPos from, BlockPos to) {
        buildArea = new BuildArea(from, to);
    }

    public static void unsetBuildArea() {
        buildArea = null;
    }

    public static class BuildArea {

        public final BlockPos from;
        public final BlockPos to;

        public BuildArea(BlockPos _from, BlockPos _to) {
            this.from = _from;
            this.to = _to;
        }
    }
}

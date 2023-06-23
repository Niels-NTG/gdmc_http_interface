package org.ntg.gdmc.gdmchttpinterface.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

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

        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, new Gson().toJson(buildArea));
    }

    public static void setBuildArea(BlockPos from, BlockPos to) {
        buildArea = new BuildArea(from, to);
    }

    public static void unsetBuildArea() {
        buildArea = null;
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class BuildArea {

        private final int xFrom;
        private final int yFrom;
        private final int zFrom;
        private final int xTo;
        private final int yTo;
        private final int zTo;

        public final transient BlockPos from;
        public final transient BlockPos to;
        public final transient ChunkPos sectionFrom;
        public final transient ChunkPos sectionTo;

        public BuildArea(BlockPos _from, BlockPos _to) {
            this.xFrom = _from.getX();
            this.yFrom = _from.getY();
            this.zFrom = _from.getZ();
            this.xTo = _to.getX();
            this.yTo = _to.getY();
            this.zTo = _to.getZ();
            this.from = _from;
            this.to = _to;
            this.sectionFrom = new ChunkPos(_from);
            this.sectionTo = new ChunkPos(_to);
        }

        public boolean isOutsideBuildArea(BlockPos pos) {
            return isOutsideBuildArea(pos.getX(), pos.getZ());
        }

        public boolean isOutsideBuildArea(int x, int z) {
            return x < from.getX() || x > to.getX() || z < from.getZ() || z > to.getZ();
        }

        public boolean isOutsideBuildArea(ChunkPos chunkPos) {
            return isOutsideBuildArea(chunkPos.getWorldPosition());
        }
    }
}

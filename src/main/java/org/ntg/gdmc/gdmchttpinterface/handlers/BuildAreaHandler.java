package org.ntg.gdmc.gdmchttpinterface.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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

        public final transient BoundingBox box;
        public final transient BlockPos from;
        public final transient BlockPos to;
        public final transient ChunkPos sectionFrom;
        public final transient ChunkPos sectionTo;


        public BuildArea(BlockPos from, BlockPos to) {
            box = BoundingBox.fromCorners(from, to);
            this.from = new BlockPos(box.minX(), box.minY(), box.minZ());
            this.to = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
            sectionFrom = new ChunkPos(this.from);
            sectionTo = new ChunkPos(this.to);
            xFrom = this.from.getX();
            yFrom = this.from.getY();
            zFrom = this.from.getZ();
            xTo = this.to.getX();
            yTo = this.to.getY();
            zTo = this.to.getZ();
        }

        public boolean isOutsideBuildArea(int x, int z) {
            return x < from.getX() || x > to.getX() || z < from.getZ() || z > to.getZ();
        }

        public boolean isOutsideBuildArea(BlockPos pos) {
            return isOutsideBuildArea(pos.getX(), pos.getZ());
        }

        public boolean isOutsideBuildArea(BoundingBox otherBox) {
            return box.maxX() < otherBox.minX() || box.minX() > otherBox.maxX() || box.maxZ() < otherBox.minZ() || box.minZ() > otherBox.maxZ();
        }
    }
}

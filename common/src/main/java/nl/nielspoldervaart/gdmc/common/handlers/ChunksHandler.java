package nl.nielspoldervaart.gdmc.common.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ChunksHandler extends HandlerBase {

    public ChunksHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        if (!httpExchange.getRequestMethod().equalsIgnoreCase("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // GET: Chunk coordinate at origin.
        int chunkX;
        int chunkZ;

        // GET: Ranges in the x and z directions (can be negative). Defaults to 1.
        int chunkDX;
        int chunkDZ;

        // GET: if true, constrain getting biomes within the current build area.
        boolean withinBuildArea;

        String dimension;

        BuildArea.BuildAreaInstance buildArea = null;
        try {
            buildArea = BuildArea.getBuildArea();
        } catch (HttpException ignored) {}

        try {
            if (queryParams.get("x") == null && buildArea != null) {
                chunkX = buildArea.sectionFrom.x;
            } else {
                chunkX = Integer.parseInt(queryParams.getOrDefault("x", "0"));
            }

            if (queryParams.get("z") == null && buildArea != null) {
                chunkZ = buildArea.sectionFrom.z;
            } else {
                chunkZ = Integer.parseInt(queryParams.getOrDefault("z", "0"));
            }

            if (queryParams.get("dx") == null && buildArea != null) {
                chunkDX = buildArea.sectionTo.x - buildArea.sectionFrom.x;
            } else {
                chunkDX = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            }

            if (queryParams.get("dz") == null && buildArea != null) {
                chunkDZ = buildArea.sectionTo.z - buildArea.sectionFrom.z;
            } else {
                chunkDZ = Integer.parseInt(queryParams.getOrDefault("dz", "1"));
            }

            withinBuildArea = Boolean.parseBoolean(queryParams.getOrDefault("withinBuildArea", "false"));

            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HttpException(message, 400);
        }

        // Check if clients wants a response in plain-text. If not, return response
        // in a binary format.
        Headers requestHeaders = httpExchange.getRequestHeaders();
        String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
        boolean returnPlainText = acceptHeader.equals("text/plain");

        // If "Accept-Encoding" header is set to "gzip" and the client expects a binary format,
        // compress the result using GZIP before sending out the response.
        String acceptEncodingHeader = getHeader(requestHeaders, "Accept-Encoding", "*");
        boolean returnCompressed = acceptEncodingHeader.equals("gzip");

        ServerLevel serverLevel = getServerLevel(dimension);

        // Gather all chunk data within the given range.
        // Constrain start and end position to that of the build area if withinBuildArea is true.
        BoundingBox box = BuildArea.clampChunksToBuildArea(createBoundingBox(
            chunkX, 0, chunkZ,
            chunkDX, 0, chunkDZ
        ), withinBuildArea);

        LinkedHashMap<ChunkPos, CompoundTag> chunkMap = new LinkedHashMap<>();
        for (int rangeZ = box.minZ(); rangeZ <= box.maxZ(); rangeZ++) {
            for (int rangeX = box.minX(); rangeX <= box.maxX(); rangeX++) {
                chunkMap.put(new ChunkPos(rangeX, rangeZ), null);
            }
        }
        chunkMap.keySet().parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverLevel.getChunk(chunkPos.x, chunkPos.z);
            CompoundTag chunkNBT = ChunkSerializer.write(serverLevel, chunk);
            chunkMap.replace(chunkPos, chunkNBT);
        });
        ListTag chunkList = new ListTag();
        chunkList.addAll(chunkMap.values());

        CompoundTag bodyNBT = new CompoundTag();
        bodyNBT.put("Chunks", chunkList);
        bodyNBT.putInt("ChunkX", box.minX());
        bodyNBT.putInt("ChunkZ", box.minZ());
        bodyNBT.putInt("ChunkDX", (box.maxX() - box.minX()) + 1);
        bodyNBT.putInt("ChunkDZ", (box.maxZ() - box.minZ()) + 1);

        // Response header and response body
        Headers responseHeaders = httpExchange.getResponseHeaders();
        if (returnPlainText) {
            String responseString = bodyNBT.toString();

            setResponseHeadersContentTypePlain(responseHeaders);
            resolveRequest(httpExchange, responseString);
            return;
        }

        setResponseHeadersContentTypeBinary(responseHeaders, returnCompressed);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (returnCompressed) {
            GZIPOutputStream dos = new GZIPOutputStream(baos);
            NbtIo.writeCompressed(bodyNBT, dos);
            dos.flush();
            byte[] responseBytes = baos.toByteArray();

            resolveRequest(httpExchange, responseBytes);
            return;
        }
        DataOutputStream dos = new DataOutputStream(baos);
        NbtIo.write(bodyNBT, dos);
        dos.flush();
        byte[] responseBytes = baos.toByteArray();

        resolveRequest(httpExchange, responseBytes);
    }
}

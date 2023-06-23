package org.ntg.gdmc.gdmchttpinterface.handlers;

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
import org.ntg.gdmc.gdmchttpinterface.handlers.BuildAreaHandler.BuildArea;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ChunkHandler extends HandlerBase {

    public ChunkHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        // query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // GET: Chunk coordinate at origin.
        int chunkX;
        int chunkZ;

        // GET: Ranges in the x and z directions (can be negative). Defaults to 1.
        int chunkDX;
        int chunkDZ;

        String dimension;

        BuildArea buildArea = BuildAreaHandler.getBuildArea();

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
                chunkDX = Math.max(buildArea.sectionTo.x - buildArea.sectionFrom.x, 0) + 1;
            } else {
                chunkDX = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            }

            if (queryParams.get("dz") == null && buildArea != null) {
                chunkDZ = Math.max(buildArea.sectionTo.z - buildArea.sectionFrom.z, 0) + 1;
            } else {
                chunkDZ = Integer.parseInt(queryParams.getOrDefault("dz", "1"));
            }
            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HttpException(message, 400);
        }

        String method = httpExchange.getRequestMethod().toLowerCase();
        if(!method.equals("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
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
        int xOffset = chunkX + chunkDX;
        int xMin = Math.min(chunkX, xOffset);
        int xMax = Math.max(chunkX, xOffset);

        int zOffset = chunkZ + chunkDZ;
        int zMin = Math.min(chunkZ, zOffset);
        int zMax = Math.max(chunkZ, zOffset);

        Map<ChunkPos, CompoundTag> chunkMap = new LinkedHashMap<>();
        for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
            for (int rangeX = xMin; rangeX < xMax; rangeX++) {
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
        bodyNBT.putInt("ChunkX", chunkX);
        bodyNBT.putInt("ChunkZ", chunkZ);
        bodyNBT.putInt("ChunkDX", chunkDX);
        bodyNBT.putInt("ChunkDZ", chunkDZ);

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
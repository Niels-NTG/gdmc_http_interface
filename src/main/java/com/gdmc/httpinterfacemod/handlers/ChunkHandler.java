package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

        try {
            chunkX = Integer.parseInt(queryParams.getOrDefault("x", "0"));
            chunkZ = Integer.parseInt(queryParams.getOrDefault("z", "0"));

            chunkDX = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            chunkDZ = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HttpException(message, 400);
        }

        String method = httpExchange.getRequestMethod().toLowerCase();
        if(!method.equals("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // Check if clients wants a response in plain-text or JSON format. If not, return response
        // in a binary format.
        Headers requestHeaders = httpExchange.getRequestHeaders();
        String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
        boolean returnPlainText = acceptHeader.equals("text/plain");
        boolean returnJson = hasJsonTypeInHeader(acceptHeader);

        // If "Accept-Encoding" header is set to "gzip" and the client expects a binary format,
        // compress the result using GZIP before sending out the response.
        String acceptEncodingHeader = getHeader(requestHeaders, "Accept-Encoding", "*");
        boolean returnCompressed = acceptEncodingHeader.equals("gzip");

        ServerLevel serverLevel = getServerLevel(dimension);

        // Gather all chunk data within the given range.
        CompletableFuture<ListTag> cfs = CompletableFuture.supplyAsync(() -> {
            int xOffset = chunkX + chunkDX;
            int xMin = Math.min(chunkX, xOffset);
            int xMax = Math.max(chunkX, xOffset);

            int zOffset = chunkZ + chunkDZ;
            int zMin = Math.min(chunkZ, zOffset);
            int zMax = Math.max(chunkZ, zOffset);
            ListTag returnList = new ListTag();
            for (int rangeZ = zMin; rangeZ < zMax; rangeZ++)
                for (int rangeX = xMin; rangeX < xMax; rangeX++) {
                    LevelChunk chunk = serverLevel.getChunk(rangeX, rangeZ);
                    CompoundTag chunkNBT = ChunkSerializer.write(serverLevel, chunk);
                    returnList.add(chunkNBT);
                }
            return returnList;
        }, mcServer);

        // block this thread until the above code has run on the main thread
        ListTag chunkList = cfs.join();

        CompoundTag bodyNBT = new CompoundTag();
        bodyNBT.put("Chunks", chunkList);
        bodyNBT.putInt("ChunkX", chunkX);
        bodyNBT.putInt("ChunkZ", chunkZ);
        bodyNBT.putInt("ChunkDX", chunkDX);
        bodyNBT.putInt("ChunkDZ", chunkDZ);

        // Response header and response body
        Headers responseHeaders = httpExchange.getResponseHeaders();
        if (returnPlainText) {
            setResponseHeadersContentTypePlain(responseHeaders);

            String responseString = bodyNBT.toString();

            resolveRequest(httpExchange, responseString);
        } else if (returnJson) {
            setResponseHeadersContentTypeJson(responseHeaders);

            String responseString = JsonParser.parseString((new JsonTagVisitor()).visit(bodyNBT)).toString();

            resolveRequest(httpExchange, responseString);
        } else {
            setResponseHeadersContentTypeBinary(responseHeaders, returnCompressed);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            if (returnCompressed) {
                NbtIo.writeCompressed(bodyNBT, dos);
            } else {
                NbtIo.write(bodyNBT, dos);
            }
            dos.flush();
            byte[] responseBytes = baos.toByteArray();

            resolveRequest(httpExchange, responseBytes);
        }
    }
}
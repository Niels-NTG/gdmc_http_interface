package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

    String dimension;
    public ChunkHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        // query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        int chunkX;
        int chunkZ;
        int chunkDX;
        int chunkDZ;
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

        // with this header we return pure NBT binary
        // if content type is application/json use that otherwise return text
        Headers requestHeaders = httpExchange.getRequestHeaders();
        String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
        boolean returnBinary = acceptHeader.equals("application/octet-stream");
        boolean returnJson = hasJsonTypeInHeader(acceptHeader);

        // construct response
        ServerLevel serverLevel = getServerLevel(dimension);

        CompletableFuture<ListTag> cfs = CompletableFuture.supplyAsync(() -> {
            int xOffset = chunkX + chunkDX;
            int xMin = Math.min(chunkX, xOffset);
            int xMax = Math.max(chunkX, xOffset);

            int zOffset = chunkZ + chunkDZ;
            int zMin = Math.min(chunkZ, zOffset);
            int zMax = Math.max(chunkZ, zOffset);
            ListTag returnList = new ListTag();
            for(int z = zMin; z < zMax; z++)
                for(int x = xMin; x < xMax; x++) {
                    LevelChunk chunk = serverLevel.getChunk(x, z);
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
        Headers headers = httpExchange.getResponseHeaders();
        if (returnBinary) {
            headers.add("Content-Type", "application/octet-stream");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            CompoundTag containterNBT = new CompoundTag();
            containterNBT.put("file", bodyNBT);
            containterNBT.write(dos);
            dos.flush();
            byte[] responseBytes = baos.toByteArray();

            resolveRequest(httpExchange, responseBytes);
        } else if (returnJson) {
            headers.add("Content-Type", "application/json; charset=UTF-8");
            String responseString = JsonParser.parseString((new JsonTagVisitor()).visit(bodyNBT)).toString();

            resolveRequest(httpExchange, responseString);
        } else {
            headers.add("Content-Type", "text/plain; charset=UTF-8");
            String responseString = bodyNBT.toString();

            resolveRequest(httpExchange, responseString);
        }
    }
}
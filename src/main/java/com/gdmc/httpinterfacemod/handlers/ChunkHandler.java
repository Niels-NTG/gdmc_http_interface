package com.gdmc.httpinterfacemod.handlers;

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
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HandlerBase.HttpException(message, 400);
        }

        String method = httpExchange.getRequestMethod().toLowerCase();
        if(!method.equals("get")) {
            throw new HandlerBase.HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // with this header we return pure NBT binary
        // if content type is application/json use that otherwise return text
        Headers requestHeaders = httpExchange.getRequestHeaders();
        String contentType = getHeader(requestHeaders, "Accept", "*/*");
        boolean RETURN_TEXT = !contentType.equals("application/octet-stream");

        // construct response
        ServerLevel serverLevel = mcServer.overworld();

        CompletableFuture<ListTag> cfs = CompletableFuture.supplyAsync(() -> {
            ListTag returnList = new ListTag();
            for(int z = chunkZ; z < chunkZ + chunkDZ; z++)
                for(int x = chunkX; x < chunkX + chunkDX; x++) {
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

        // headers and body
        Headers headers = httpExchange.getResponseHeaders();

        if(RETURN_TEXT) {
            headers.add("Content-Type", "text/plain; charset=UTF-8");
            String responseString = bodyNBT.toString();

            resolveRequest(httpExchange, responseString);
        } else {
            headers.add("Content-Type", "application/octet-stream");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            CompoundTag containterNBT = new CompoundTag();
            containterNBT.put("file", bodyNBT);
            containterNBT.write(dos);
            dos.flush();
            byte[] responseBytes = baos.toByteArray();

            resolveRequest(httpExchange, responseBytes);
        }
    }
}
package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.handlers.BuildAreaHandler.BuildArea;
import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;

import java.io.IOException;
import java.util.Map;

// https://github.com/Niels-NTG/gdmc_http_interface/pull/19

public class HeightmapHandler extends HandlerBase {

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        String method = httpExchange.getRequestMethod().toLowerCase();

        if (!method.equals("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // Get query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());
        // Try to parse a type argument from them
        String heightmapTypeString = queryParams.getOrDefault("type", "WORLD_SURFACE");
        Types heightmapType;
        // Check if the type is a valid heightmap type
        try {
            // If so, store the type object
            heightmapType = Types.valueOf(heightmapTypeString);
        } catch (IllegalArgumentException e) {
            // Otherwise, throw an error
            throw new HttpException("heightmap type " + heightmapTypeString + " is not supported.", 400);
        }

        String dimension = queryParams.getOrDefault("dimension", null);

        // Get the build area
        BuildArea buildArea = BuildAreaHandler.getBuildArea();
        if (buildArea == null) {
            throw new HttpException("No build area is specified. Use the setbuildarea command inside Minecraft to set a build area.", 404);
        }

        // Get a reference to the map/level
        ServerLevel serverlevel = getServerLevel(dimension);

        // Get the heightmap of that type
        int[][] heightmap = getHeightmap(buildArea, serverlevel, heightmapType);

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }


    private static int[][] getHeightmap(BuildArea buildArea, ServerLevel serverlevel, Types heightmapType) {

        // Get the x/z size of the build area
        int xSize = buildArea.to.getX() - buildArea.from.getX() + 1;
        int zSize = buildArea.to.getZ() - buildArea.from.getZ() + 1;
        // Create the 2D array to store the heightmap data
        int[][] heightmap = new int[xSize][zSize];

        // Get the number of chunks
        int xChunkCount = Math.floorDiv(buildArea.to.getX(), 16) - Math.floorDiv(buildArea.from.getX(), 16) + 1;
        int zChunkCount = Math.floorDiv(buildArea.to.getZ(), 16) - Math.floorDiv(buildArea.from.getZ(), 16) + 1;

        // Get the chunk x and z of the chunk at the lowest x and z
        int minChunkX = Math.floorDiv(buildArea.from.getX(), 16);
        int minChunkZ = Math.floorDiv(buildArea.from.getZ(), 16);

        // For every chunk in the build area
        for (int chunkX = minChunkX; chunkX < xChunkCount + minChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ < zChunkCount + minChunkZ; ++chunkZ) {

                // Get the chunk
                LevelChunk chunk = serverlevel.getChunk(chunkX, chunkZ);

                // Get the heightmap of type
                Heightmap chunkHeightmap = chunk.getOrCreateHeightmapUnprimed(heightmapType);

                // For every combination of x and z in that chunk
                int chunkMinX = chunkX * 16;
                int chunkMinZ = chunkZ * 16;
                for (int x = chunkMinX; x < chunkMinX + 16; ++x) {
                    for (int z = chunkMinZ; z < chunkMinZ + 16; ++z) {
                        // If the column is out of bounds skip it
                        if (x < buildArea.from.getX() || x > buildArea.to.getX() || z < buildArea.from.getZ() || z > buildArea.to.getZ()) {
                            continue;
                        }
                        // Set the value in the heightmap array
                        heightmap[x - buildArea.from.getX()][z - buildArea.from.getZ()] = chunkHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ);
                    }
                }
            }
        }

        // Return the completed heightmap array
        return heightmap;
    }
}

package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HeightmapHandler extends HandlerBase {

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);
    }



    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        String method = httpExchange.getRequestMethod().toLowerCase();

        if (method.equals("get")) {

            // Get the build area
            var buildArea = BuildAreaHandler.getBuildArea();
            if (buildArea == null) {
                throw new HttpException("No build area is specified. Use the buildarea command inside Minecraft to set a build area.", 404);
            }

            // Get a reference to the map/level
            ServerLevel serverlevel = mcServer.overworld();

            // Get query parameters
            Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());
            // Try to parse a type argument from them
            var heightmapTypeString = queryParams.getOrDefault("type", "MOTION_BLOCKING");
            Types heightmapType;
            // Check if the type is a valid heightmap type
            try {
                // If so, store the type object
                heightmapType = Types.valueOf(heightmapTypeString);
            } catch (IllegalArgumentException e) {
                // Otherwise, throw an error
                String message = "Could not parse query parameter: " + e.getMessage();
                throw new HandlerBase.HttpException(message, 400);
            }

            // Get the heightmap of that type
            var heightmap = getHeightmap(buildArea, serverlevel, heightmapType);

            // Convert the 2D int array to a 2D JsonArray
            JsonArray responseArray = new JsonArray();
            for (int[] row : heightmap) {
                JsonArray jsonRow = new JsonArray();
                for (int val : row) {
                    jsonRow.add(val);
                }
                responseArray.add(jsonRow);
            }

            // Respond with that array as a string
            Headers responseHeaders = httpExchange.getResponseHeaders();
            setDefaultResponseHeaders(responseHeaders);
            resolveRequest(httpExchange, responseArray.toString());
        }
        else {
            throw new HttpException("Method not allowed. Only GET or POST requests are supported.", 405);
        }
    }


    public int[][] getHeightmap(BuildAreaHandler.BuildArea buildArea, ServerLevel serverlevel, Types heightmapType) {

        // Get the x/z size of the build area
        var xSize = buildArea.getxTo() - buildArea.getxFrom() + 1;
        var zSize = buildArea.getzTo() - buildArea.getzFrom() + 1;
        // Create the 2D array to store the heightmap data
        var heightmap = new int[xSize][zSize];

        // Get the number of chunks
        int xChunkCount = Math.floorDiv(buildArea.getxTo(), 16) - Math.floorDiv(buildArea.getxFrom(), 16) + 1;
        int zChunkCount = Math.floorDiv(buildArea.getzTo(), 16) - Math.floorDiv(buildArea.getzFrom(), 16) + 1;

        // Get the chunk x and z of the chunk at the lowest x and z
        int minChunkX = Math.floorDiv(buildArea.getxFrom(), 16);
        int minChunkZ = Math.floorDiv(buildArea.getzFrom(), 16);

        // For every chunk in the build area
        for (int chunkX = minChunkX; chunkX < xChunkCount + minChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ < zChunkCount + minChunkZ; ++chunkZ) {

                // Get the chunk
                var chunk = serverlevel.getChunk(chunkX, chunkZ);

                // Get the heightmap of the appropriate type
                HashMap<Types, Heightmap> heightmaps = new HashMap<>();
                chunk.getHeightmaps().forEach(item -> heightmaps.put(item.getKey(), item.getValue()));
                var chunkHeightmap = heightmaps.get(heightmapType);

                // For every combination of x and z in that chunk
                int chunkMinX = chunkX * 16;
                int chunkMinZ = chunkZ * 16;
                for (int x = chunkMinX; x < chunkMinX + 16; ++x) {
                    for (int z = chunkMinZ; z < chunkMinZ + 16; ++z) {
                        // If the column is out of bounds skip it
                        if (x < buildArea.getxFrom() || x > buildArea.getxTo() || z < buildArea.getzFrom() || z > buildArea.getzTo()) {
                            continue;
                        }
                        // Set the value in the heightmap array
                        heightmap[x - buildArea.getxFrom()][z - buildArea.getzFrom()] = chunkHeightmap.getHighestTaken(x - chunkMinX, z - chunkMinZ);
                    }
                }
            }
        }

        // Return the completed heightmap array
        return heightmap;
    }
}

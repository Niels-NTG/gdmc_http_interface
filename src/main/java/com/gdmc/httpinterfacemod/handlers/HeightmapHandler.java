package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class HeightmapHandler extends HandlerBase {

    private HashMap<ResourceLocation, Block[]> groundBlocks = new HashMap<>();

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);

        // Used
        groundBlocks.put(Biomes.BADLANDS.location(), new Block[]{Blocks.RED_SAND, Blocks.RED_SAND, Blocks.RED_SAND, Blocks.RED_SANDSTONE});
        groundBlocks.put(Biomes.BAMBOO_JUNGLE.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.BEACH.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.SAND, Blocks.STONE});
        groundBlocks.put(Biomes.BIRCH_FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.COLD_OCEAN.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DARK_FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DEEP_COLD_OCEAN.location(), new Block[]{Blocks.CLAY, Blocks.CLAY, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DEEP_FROZEN_OCEAN.location(), new Block[]{Blocks.CLAY, Blocks.CLAY, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DEEP_LUKEWARM_OCEAN.location(), new Block[]{Blocks.CLAY, Blocks.CLAY, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DEEP_OCEAN.location(), new Block[]{Blocks.CLAY, Blocks.CLAY, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.DESERT.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.SAND, Blocks.SANDSTONE});
        groundBlocks.put(Biomes.ERODED_BADLANDS.location(), new Block[]{Blocks.RED_SAND, Blocks.RED_SAND, Blocks.RED_SAND, Blocks.RED_SANDSTONE});
        groundBlocks.put(Biomes.FLOWER_FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.FROZEN_OCEAN.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.FROZEN_PEAKS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.FROZEN_RIVER.location(), new Block[]{Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.GROVE.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.ICE_SPIKES.location(), new Block[]{Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.JAGGED_PEAKS.location(), new Block[]{Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.STONE, Blocks.STONE});
        groundBlocks.put(Biomes.JUNGLE.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.LUKEWARM_OCEAN.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.MANGROVE_SWAMP.location(), new Block[]{Blocks.MUD, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.MEADOW.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.MUSHROOM_FIELDS.location(), new Block[]{Blocks.MYCELIUM, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.OCEAN.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.OLD_GROWTH_BIRCH_FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.OLD_GROWTH_PINE_TAIGA.location(), new Block[]{Blocks.PODZOL, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA.location(), new Block[]{Blocks.PODZOL, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.PLAINS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.RIVER.location(), new Block[]{Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SAVANNA.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SAVANNA_PLATEAU.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SNOWY_BEACH.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.SAND, Blocks.STONE});
        groundBlocks.put(Biomes.SNOWY_PLAINS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SNOWY_SLOPES.location(), new Block[]{Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.STONE, Blocks.STONE});
        groundBlocks.put(Biomes.SNOWY_TAIGA.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SPARSE_JUNGLE.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.STONY_PEAKS.location(), new Block[]{Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE});
        groundBlocks.put(Biomes.STONY_SHORE.location(), new Block[]{Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE});
        groundBlocks.put(Biomes.SUNFLOWER_PLAINS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.SWAMP.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.TAIGA.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.WARM_OCEAN.location(), new Block[]{Blocks.SAND, Blocks.SAND, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.WINDSWEPT_FOREST.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.WINDSWEPT_HILLS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.WINDSWEPT_SAVANNA.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
        groundBlocks.put(Biomes.WINDSWEPT_GRAVELLY_HILLS.location(), new Block[]{Blocks.GRAVEL, Blocks.GRAVEL, Blocks.GRAVEL, Blocks.STONE});
        groundBlocks.put(Biomes.WOODED_BADLANDS.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.TERRACOTTA, Blocks.TERRACOTTA, Blocks.TERRACOTTA});

        // Unused
        groundBlocks.put(Biomes.LUSH_CAVES.location(), new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.STONE});
    }



    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        // Get the build area
        var buildArea = BuildAreaHandler.getBuildArea();
        if (buildArea == null) {
            throw new HttpException("No build area is specified. Use the buildarea command inside Minecraft to set a build area.", 404);
        }

        // Get a reference to the map/level
        ServerLevel serverlevel = mcServer.overworld();

        // Query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        String method = httpExchange.getRequestMethod().toLowerCase();

        if (method.equals("post")) {
            // Get the body of the request as a string
            InputStream inputStream = httpExchange.getRequestBody();
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            String requestBody = scanner.hasNext() ? scanner.next() : "";
            inputStream.close();

            System.out.println(requestBody);

            // Convert it to a JsonObject
            JsonObject requestJson = new JsonParser().parse(requestBody).getAsJsonObject();

            JsonArray heightmapJsonArray = requestJson.get("heightmap").getAsJsonArray();
            int rows = heightmapJsonArray.size();
            int cols = heightmapJsonArray.get(0).getAsJsonArray().size();

            // System.out.println(Integer.toString(buildArea.getxTo() - buildArea.getxFrom() + 1));
            // System.out.println(Integer.toString(buildArea.getzTo() - buildArea.getzFrom() + 1));
            // System.out.println(Integer.toString(rows));
            // System.out.println(Integer.toString(cols));

            // If the dimensions of the provided heightmap don't match the build area
            if (
                rows != buildArea.getxTo() - buildArea.getxFrom() + 1 ||
                    cols != buildArea.getzTo() - buildArea.getzFrom() + 1
            ) {
                // Throw an error
                String message = "Dimensions of provided heightmap doesn't match size of buildarea";
                throw new HandlerBase.HttpException(message, 400);
            }

            // Get the old heightmap
            var oldHeightmap = getHeightmap(buildArea, serverlevel, Types.OCEAN_FLOOR);

            // Convert the JsonArray into a 2D int array
            int[][] newHeightmap = new int[rows][cols];
            for (int i = 0; i < rows; i++) {
                JsonArray row = heightmapJsonArray.get(i).getAsJsonArray();
                for (int j = 0; j < cols; j++) {
                    newHeightmap[i][j] = row.get(j).getAsInt();
                }
            }

            // Initialize the blocks placed counter
            int blocksPlaced = 0;

            // For each column in the buildarea
            for (int x = buildArea.getxFrom(); x <= buildArea.getxTo(); ++x) {
                for (int z = buildArea.getzFrom(); z <= buildArea.getzTo(); ++z) {
                    // Create a blockpos variable to be incremented
                    BlockPos blockPos = new BlockPos(x, 0, z);

                    var biome = serverlevel.getBiome(blockPos);
                    var biomeGroundBlocks = groundBlocks.get(biome.unwrapKey().get().location());

                    // Read the new height from the heightmap
                    var newHeight = newHeightmap[x - buildArea.getxFrom()][z - buildArea.getzFrom()];
                    var oldHeight = oldHeightmap[x - buildArea.getxFrom()][z - buildArea.getzFrom()];
                    var startHeight = Math.min(Math.max(newHeight, oldHeight), buildArea.getyTo());
                    var endHeight = Math.max(Math.min(newHeight, oldHeight), buildArea.getyFrom());
                    endHeight -= biomeGroundBlocks.length - 1;
                    System.out.println("New Height: " + Integer.toString(newHeight));
                    System.out.println("Old Height: " + Integer.toString(oldHeight));

                    var extendingToMeetGround = false;
                    // For every block in that column
                    for (int y = startHeight; y >= endHeight; --y) {
                        // Update the blockpos to the current y value
                        blockPos = blockPos.atY(y);
                        var block = serverlevel.getBlockState(blockPos).getBlock();

                        if (extendingToMeetGround && block.hashCode() != Blocks.AIR.hashCode()) {
                            break;
                        }

                        // If the ground needs to be raised
                        if (newHeight > oldHeight) {
                            if (startHeight - y < biomeGroundBlocks.length) {
                                int index = startHeight - y;
                                block = biomeGroundBlocks[index];
                            }
                            else {
                                block = Blocks.STONE;
                            }
                        }
                        // If the ground needs to be lowered
                        else {
                            if (y - endHeight < biomeGroundBlocks.length) {
                                int index = biomeGroundBlocks.length - 1 - (y-endHeight);
                                block = biomeGroundBlocks[index];
                            }
                            else {
                                block = Blocks.AIR;
                            }
                        }
                        if (y == endHeight) {
                            extendingToMeetGround = true;
                            --endHeight;
                        }
                        serverlevel.setBlockAndUpdate(blockPos, block.defaultBlockState());
                        ++blocksPlaced;
                    }
                }
            }

            Headers responseHeaders = httpExchange.getResponseHeaders();
            setDefaultResponseHeaders(responseHeaders);
            resolveRequest(httpExchange, Integer.toString(blocksPlaced));
        }
        else if (method.equals("get")) {
            // Try to parse a type argument from the query params
            var heightmapTypeString = queryParams.getOrDefault("type", "MOTION_BLOCKING");
            Types heightmapType;
            try {
                heightmapType = Types.valueOf(heightmapTypeString);
            } catch (IllegalArgumentException e) {
                // If it's not a valid heightmap type, throw an error
                String message = "Could not parse query parameter: " + e.getMessage();
                throw new HandlerBase.HttpException(message, 400);
            }

            // Get the heightmap
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

        return heightmap;
    }
}

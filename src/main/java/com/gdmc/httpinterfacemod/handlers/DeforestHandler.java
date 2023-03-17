package com.gdmc.httpinterfacemod.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DeforestHandler extends HandlerBase {
    public DeforestHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {
        String method = httpExchange.getRequestMethod().toLowerCase();

        if (!method.equals("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        var buildArea = BuildAreaHandler.getBuildArea();
        if (buildArea == null) {
            throw new HttpException("No build area is specified. Use the buildarea command inside Minecraft to set a build area.", 404);
        }

        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        setResponseHeadersContentTypePlain(responseHeaders);

        int replaceCount = deforest(buildArea);
        resolveRequest(httpExchange, Integer.toString(replaceCount));
    }

    private Block[] replaceableBlocks = {
            Blocks.OAK_LOG,
            Blocks.SPRUCE_LOG,
            Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG,
            Blocks.DARK_OAK_LOG,
            Blocks.ACACIA_LOG,
            Blocks.MANGROVE_LOG,
            Blocks.CACTUS,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.PUMPKIN,
            Blocks.MELON,
            Blocks.MUSHROOM_STEM,
            Blocks.BROWN_MUSHROOM_BLOCK,
            Blocks.RED_MUSHROOM_BLOCK,
    };

    private Block[] stoppers = {
            Blocks.STONE,
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,
            Blocks.SAND,
            Blocks.SANDSTONE,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.COARSE_DIRT,
            Blocks.PODZOL,
            Blocks.MYCELIUM
    };

    private Block[] additionalReplaceableBlocks = {
            Blocks.DEAD_BUSH,
            Blocks.DANDELION,
            Blocks.POPPY,
            Blocks.BLUE_ORCHID,
            Blocks.ALLIUM,
            Blocks.AZURE_BLUET,
            Blocks.RED_TULIP,
            Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER,
            Blocks.LILY_OF_THE_VALLEY,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.SUGAR_CANE,
            Blocks.GRASS,
            Blocks.SUNFLOWER,
            Blocks.LILAC,
            Blocks.ROSE_BUSH,
            Blocks.PEONY,
            Blocks.TALL_GRASS,
            Blocks.LARGE_FERN,
            Blocks.FERN,
    };

    private final HashSet<Integer> replaceableBlocksSet = new HashSet<>(
        Arrays.asList(replaceableBlocks).stream().map(Block::hashCode).collect(Collectors.toList())
    );
    private final HashSet<Integer> stoppersSet = new HashSet<>(
        Arrays.asList(stoppers).stream().map(Block::hashCode).collect(Collectors.toList())
    );

    private int deforest(BuildAreaHandler.BuildArea buildArea) {
        var boundingBox = new BoundingBox(
            buildArea.getxFrom(),
            buildArea.getyFrom(),
            buildArea.getzFrom(),
            buildArea.getxTo(),
            buildArea.getyTo(),
            buildArea.getzTo()
        );
        System.out.println("Starting deforestation...");
        return fillBlocks(boundingBox);
    }

    public boolean isBlockInArray(Block[] blockArray, Block target) {

        for (Block replaceableBlock : blockArray) {
            if (replaceableBlock.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private int fillBlocks(BoundingBox boundingBox) {

        // Get a reference to the map/level
        ServerLevel serverlevel = mcServer.overworld();

        // Get the number of chunks
        int xChunkCount = Math.floorDiv(boundingBox.maxX(), 16) - Math.floorDiv(boundingBox.minX(), 16) + 1;
        int zChunkCount = Math.floorDiv(boundingBox.maxZ(), 16) - Math.floorDiv(boundingBox.minZ(), 16) + 1;

        // Get the chunk x and z of the chunk at the lowest x and z
        int minChunkX = Math.floorDiv(boundingBox.minX(), 16);
        int minChunkZ = Math.floorDiv(boundingBox.minZ(), 16);

        // Initialize the blocks placed counter
        int blocksPlaced = 0;

        // For every chunk in the build area
        for (int chunkX = minChunkX; chunkX < xChunkCount + minChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ < zChunkCount + minChunkZ; ++chunkZ) {
                // System.out.println("Chunk X: " + Integer.toString(chunkX) + ", Chunk Z: " + Integer.toString(chunkZ));
                // Get the chunk
                var chunk = serverlevel.getChunk(chunkX, chunkZ);
                // Get the "MOTION_BLOCKING" heightmap
                HashMap<Types, Heightmap> heightmaps = new HashMap<>();
                chunk.getHeightmaps().forEach(item -> heightmaps.put(item.getKey(), item.getValue()));
                var heightmap = heightmaps.get(Types.MOTION_BLOCKING);
                // For every combination of x and z in that chunk
                int chunkMinX = chunkX * 16;
                int chunkMinZ = chunkZ * 16;

                // System.out.println("Chunk Min X: " + Integer.toString(chunkMinX) + ", Chunk Min Z: " + Integer.toString(chunkMinZ));
                for (int x = chunkMinX; x < chunkMinX + 16; ++x) {
                    for (int z = chunkMinZ; z < chunkMinZ + 16; ++z) {

                        // If the column is out of bounds skip it
                        if (x < boundingBox.minX() || x > boundingBox.maxX() || z < boundingBox.minZ() || z > boundingBox.maxZ()) {
                            continue;
                        }

                        // Get the value of the heightmap for that column
                        int highestY = heightmap.getHighestTaken(x - chunkMinX, z - chunkMinZ);

                        // System.out.println(
                        //         "X: " + Integer.toString(x) + ", Z: " + Integer.toString(z) + ", Height: " + highestY
                        // );

                        // Create a blockpos variable to be incremented
                        BlockPos blockPos = new BlockPos(x, boundingBox.maxY() - 1, z);
                        // For every block in the column starting at the highest and moving down
                        for (int y = highestY; y >= boundingBox.minY(); --y) {
                            // Update the blockpos to the current y value
                            blockPos = blockPos.atY(y);

                            // Get the block
                            var block = chunk.getBlockState(blockPos).getBlock();
                            // If the block is in the "stoppers" array, move to next column
//                            if (isBlockInArray(stoppers, block)) break;
                            if (stoppersSet.contains(block.hashCode())) break;
//                             If the block is in the "replaceable" array
//                            if (isBlockInArray(replaceableBlocks, block)) {
                            if (replaceableBlocksSet.contains(block.hashCode())) {

                                // Remove any existing block entities
                                // BlockEntity blockentity = chunk.getBlockEntity(blockPos);
                                // Clearable.tryClear(blockentity);

                                // Replace the block
                                if (serverlevel.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState())) {
                                    // Increment the counter
                                    ++blocksPlaced;
                                    // Print a message every 1000 blocks placed
                                    if (blocksPlaced % 1000 == 0) {
                                        System.out.println(blocksPlaced);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Print that it's complete
        System.out.println("Deforestation complete!");

        // Return the blocks placed count
        if (blocksPlaced == 0) {
            return -1;
        } else {
            return blocksPlaced;
        }
    }
}
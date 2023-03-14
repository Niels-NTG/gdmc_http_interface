package com.gdmc.httpinterfacemod.handlers;

import com.google.common.collect.Lists;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

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

        // Setup
        List<BlockPos> list = Lists.newArrayList();
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
//                System.out.println("Chunk X: " + Integer.toString(chunkX) + ", Chunk Z: " + Integer.toString(chunkZ));
                // Get the chunk
                var chunk = serverlevel.getChunk(chunkX,chunkZ);
                // Get the "MOTION_BLOCKING" heightmap
                Map<Types, Heightmap> heightmaps = new HashMap<Types, Heightmap>();
                chunk.getHeightmaps().forEach(item -> heightmaps.put(item.getKey(), item.getValue()));
                var heightmap = heightmaps.get(Types.MOTION_BLOCKING);
                // For every combination of x and z in that chunk
                int chunkMinX = chunkX * 16;
                int chunkMinZ = chunkZ * 16;

//                System.out.println("Chunk Min X: " + Integer.toString(chunkMinX) + ", Chunk Min Z: " + Integer.toString(chunkMinZ));
                for (int x = chunkMinX; x < chunkMinX + 16; ++x) {
                    for (int z = chunkMinZ; z < chunkMinZ + 16; ++z) {

                        // If the column is out of bounds skip it
                        if (x < boundingBox.minX() || x >= boundingBox.maxX() || z < boundingBox.minZ() || z >= boundingBox.maxZ()) {
                            continue;
                        }

                        // Get the value of the heightmap for that column
                        int highestY = heightmap.getHighestTaken(x - chunkMinX, z - chunkMinZ);

//                        System.out.println(
//                                "X: " + Integer.toString(x) + ", Z: " + Integer.toString(z) + ", Height: " + highestY
//                        );

                        // Create a blockpos variable to be incremented
                        BlockPos blockPos = new BlockPos(x, boundingBox.maxY() - 1, z);
                        // For every block in the column starting at the highest and moving down
                        for (int y = highestY; y >= boundingBox.minY(); --y) {
                            // Update the blockpos to the current y value
                            blockPos = blockPos.atY(y);

                            // Get the block
                            var block = chunk.getBlockState(blockPos).getBlock();
                            // If it is air, move down
                            if (block.equals(Blocks.AIR)) continue;
                            // If the block is in the "stoppers" array, move to next column
                            if (isBlockInArray(stoppers, block)) break;
                            // If the block is in the "replaceable" array
                            if (isBlockInArray(replaceableBlocks, block)) {
                                // Replace it
                                BlockEntity blockentity = serverlevel.getBlockEntity(blockPos);
                                Clearable.tryClear(blockentity);
                                if (serverlevel.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState())) {
                                    list.add(blockPos.immutable());
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

    static enum Mode {
        REPLACE((p_137433_, p_137434_, p_137435_, p_137436_) -> {
            return p_137435_;
        }),
        OUTLINE((p_137428_, p_137429_, p_137430_, p_137431_) -> {
            return p_137429_.getX() != p_137428_.minX() && p_137429_.getX() != p_137428_.maxX() && p_137429_.getY() != p_137428_.minY() && p_137429_.getY() != p_137428_.maxY() && p_137429_.getZ() != p_137428_.minZ() && p_137429_.getZ() != p_137428_.maxZ() ? null : p_137430_;
        }),
        DESTROY((p_137418_, p_137419_, p_137420_, p_137421_) -> {
            p_137421_.destroyBlock(p_137419_, true);
            return p_137420_;
        });

        public final SetBlockCommand.Filter filter;

        private Mode(SetBlockCommand.Filter p_137416_) {
            this.filter = p_137416_;
        }
    }
}

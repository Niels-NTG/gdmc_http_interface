package org.ntg.gdmc.gdmchttpinterface.handlers;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.ntg.gdmc.gdmchttpinterface.utils.BuildArea;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.ntg.gdmc.gdmchttpinterface.utils.TagUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class BlocksHandler extends HandlerBase {

    private final CommandSourceStack cmdSrc;
    private final LivingEntity blockPlaceEntity;

    // PUT/GET: x, y, z positions
    private int x;
    private int y;
    private int z;

    // GET: Ranges in the x, y, z directions (can be negative). Defaults to 1.
    private int dx;
    private int dy;
    private int dz;

    // GET: Whether to include block state https://minecraft.fandom.com/wiki/Block_states
    private boolean includeState;

    // GET: Whether to include block entity data https://minecraft.fandom.com/wiki/Chunk_format#Block_entity_format
    private boolean includeData;

    // PUT: Defaults to true. If true, update neighbouring blocks after placement.
    private boolean doBlockUpdates;

    // PUT: Defaults to false. If true, block updates cause item drops after placement.
    private boolean spawnDrops;

    // PUT: Overrides both doBlockUpdates and spawnDrops if set. For more information see #getBlockFlags and
    // https://minecraft.fandom.com/wiki/Block_update
    private int customFlags; // -1 == no custom flags

    // PUT/GET is true, constrain placement/getting blocks within the current build area.
    private boolean withinBuildArea;

    private String dimension;

    public BlocksHandler(MinecraftServer mcServer) {
        super(mcServer);
        cmdSrc = createCommandSource("GDMC-BlockHandler", dimension);
        blockPlaceEntity = createLivingEntity(dimension);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        // query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        try {
            x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
            y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
            z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

            dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
            dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

            includeState = Boolean.parseBoolean(queryParams.getOrDefault("includeState", "false"));

            includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));

            doBlockUpdates = Boolean.parseBoolean(queryParams.getOrDefault("doBlockUpdates", "true"));

            spawnDrops = Boolean.parseBoolean(queryParams.getOrDefault("spawnDrops", "false"));

            customFlags = Integer.parseInt(queryParams.getOrDefault("customFlags", "-1"), 2);

            withinBuildArea = Boolean.parseBoolean(queryParams.getOrDefault("withinBuildArea", "false"));

            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            throw new HttpException("Could not parse query parameter: " + e.getMessage(), 400);
        }

        JsonArray responseObject;

        switch (httpExchange.getRequestMethod().toLowerCase()) {
            case "put" -> {
                responseObject = putBlocksHandler(httpExchange.getRequestBody());
            }
            case "get" -> {
                responseObject = getBlocksHandler();
            }
            default -> throw new HttpException("Method not allowed. Only PUT and GET requests are supported.", 405);
        }

        // Response headers
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, responseObject.toString());
    }

    /**
     * Place blocks any number of blocks into the world
     *
     * @param requestBody request body of block placement instructions
     * @return block placement results
     */
    private JsonArray putBlocksHandler(InputStream requestBody) {
        JsonArray blockPlacementList = parseJsonArray(requestBody);

        JsonArray returnValues = new JsonArray();

        // Create instance of CommandSourceStack to use as a point of origin for any relative positioned blocks.
        CommandSourceStack commandSourceStack = cmdSrc.withPosition(new Vec3(x, y, z));

        ServerLevel serverLevel = getServerLevel(dimension);

        int blockFlags = customFlags >= 0 ? customFlags : getBlockFlags(doBlockUpdates, spawnDrops);

        LinkedHashMap<BlockPos, BlockPlacementInstruction> placementInstructions = new LinkedHashMap<>();
        HashMap<ChunkPos, LevelChunk> chunkPosMap = new HashMap<>();
        for (JsonElement blockPlacementInput : blockPlacementList) {
            BlockPlacementInstruction placementInstruction = new BlockPlacementInstruction(blockPlacementInput.getAsJsonObject(), commandSourceStack);
            placementInstructions.put(placementInstruction.blockPos, placementInstruction);
        }
        placementInstructions.values().parallelStream().forEach(placementInstruction -> {
            placementInstruction.parse();
            if (placementInstruction.isValid) {
                chunkPosMap.putIfAbsent(placementInstruction.chunkPos, serverLevel.getChunk(placementInstruction.chunkPos.x, placementInstruction.chunkPos.z));
            }
            placementInstruction.updateNeighborsBlocks(serverLevel, blockFlags, placementInstructions);
        });
        for (BlockPlacementInstruction placementInstruction : placementInstructions.values()) {
            placementInstruction.setBlock(
                serverLevel,
                blockFlags
            );
        }
        placementInstructions.values().parallelStream().forEach(placementInstruction -> placementInstruction.setBlockNBT(serverLevel, chunkPosMap.get(placementInstruction.chunkPos), blockFlags));

        for (BlockPlacementInstruction placementInstruction : placementInstructions.values()) {
            returnValues.add(placementInstruction.getResult());
        }
        return returnValues;
    }

    /**
     * Get information on one of more blocks in the world.
     *
     * @return list of block information
     */
    private JsonArray getBlocksHandler() {

        JsonArray jsonArray = new JsonArray();

        ServerLevel serverLevel = getServerLevel(dimension);

        // Calculate boundaries of area of blocks to gather information on.
        BoundingBox box = BuildArea.clampToBuildArea(createBoundingBox(x, y, z, dx, dy, dz), withinBuildArea);
        // Return empty list if entire requested area is outside of build area (if withinBuildArea is true).
        if (box == null) {
            return jsonArray;
        }

        // Create ordered map to store information for each position within the given area,
        // as well as a map containing the chunks of this area that this block information
        // will be gathered from. Using a map structure allows this to be resolved in
        // parallel, which is significantly faster than doing the same sequentially.
        LinkedHashMap<BlockPos, JsonObject> blockPosMap = new LinkedHashMap<>();
        HashMap<ChunkPos, LevelChunk> chunkPosMap = new HashMap<>();
        for (int rangeX = box.minX(); rangeX <= box.maxX(); rangeX++) {
            for (int rangeY = box.minY(); rangeY <= box.maxY(); rangeY++) {
                for (int rangeZ = box.minZ(); rangeZ <= box.maxZ(); rangeZ++) {
                    BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
                    blockPosMap.put(blockPos, null);
                    chunkPosMap.put(new ChunkPos(blockPos), null);
                }
            }
        }
        chunkPosMap.keySet().parallelStream().forEach(chunkPos -> chunkPosMap.replace(chunkPos, serverLevel.getChunk(chunkPos.x, chunkPos.z)));
        blockPosMap.keySet().parallelStream().forEach(blockPos -> {
            LevelChunk levelChunk = chunkPosMap.get(new ChunkPos(blockPos));

            String blockId = getBlockAsStr(blockPos, levelChunk);
            JsonObject json = new JsonObject();
            json.addProperty("id", blockId);
            json.addProperty("x", blockPos.getX());
            json.addProperty("y", blockPos.getY());
            json.addProperty("z", blockPos.getZ());
            if (includeState) {
                json.add("state", getBlockStateAsJsonObject(blockPos, levelChunk));
            }
            if (includeData) {
                json.addProperty("data", getBlockDataAsStr(blockPos, levelChunk));
            }
            blockPosMap.replace(blockPos, json);
        });
        for (JsonObject blockJson : blockPosMap.values()) {
            jsonArray.add(blockJson);
        }

        return jsonArray;
    }

    private static BlockState getBlockStateAtPosition(BlockPos pos, LevelChunk levelChunk) {
        return levelChunk.getBlockState(pos);
    }

    /**
     * Parse block position x y z.
     * Valid values may be any positive or negative integer and can use tilde or caret notation.
     * see: <a href="https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>
     *
     * @param str                       {@code String} which may or may not contain a valid block position coordinate.
     * @param commandSourceStack        Origin for relative coordinates.
     * @return Valid {@link BlockPos}.
     * @throws CommandSyntaxException   If input string cannot be parsed into a valid {@link BlockPos}.
     */
    private static BlockPos getBlockPosFromString(String str, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        StringReader blockPosStringReader = new StringReader(str);
        Coordinates coordinates = BlockPosArgument.blockPos().parse(blockPosStringReader);
        blockPosStringReader.skip();
        return coordinates.getBlockPos(commandSourceStack);
    }

    /**
     * @param json  Valid flat {@link JsonObject} of keys with primitive values (Strings, numbers, booleans)
     * @return      {@code String} which can be parsed by {@link BlockStateParser} and should be the same as the return value of {@link BlockState#toString()} of the {@link BlockState} resulting from that parser.
     */
    private static String getBlockStateStringFromJSONObject(JsonObject json) {
        ArrayList<String> blockStateList = new ArrayList<>(json.size());
        for (Map.Entry<String, JsonElement> element : json.entrySet()) {
            blockStateList.add(element.getKey() + "=" + element.getValue());
        }
        return '[' + String.join(",", blockStateList) + ']';
    }

    /**
     * @param pos           Position of block in the world.
     * @param levelChunk    Chunk to request block state from
     * @return      Namespaced name of the block material.
     */
    private static String getBlockAsStr(BlockPos pos, LevelChunk levelChunk) {
        BlockState bs = getBlockStateAtPosition(pos, levelChunk);
        return Objects.requireNonNull(getBlockRegistryName(bs));
    }

    /**
     * @param pos           Position of block in the world.
     * @param levelChunk    Chunk to request block state from
     * @return      {@link JsonObject} containing the block state data of the block at the given position.
     */
    private static JsonObject getBlockStateAsJsonObject(BlockPos pos, LevelChunk levelChunk) {
        BlockState bs = getBlockStateAtPosition(pos, levelChunk);
        JsonObject stateJsonObject = new JsonObject();
        bs.getValues().entrySet().stream().map(propertyToStringPairFunction).filter(Objects::nonNull).forEach(pair -> stateJsonObject.add(pair.getKey(), new JsonPrimitive(pair.getValue())));
        return stateJsonObject;
    }

    /**
     * @param pos           Position of block in the world.
     * @param levelChunk    Chunk to request block state from
     * @return      {@link String} containing the block entity data of the block at the given position.
     */
    private static String getBlockDataAsStr(BlockPos pos, LevelChunk levelChunk) {
        String str = "{}";
        BlockEntity blockEntity = levelChunk.getExistingBlockEntity(pos);
        if (blockEntity != null) {
            CompoundTag tags = blockEntity.saveWithoutMetadata();
            str = tags.getAsString();
        }
        return str;
    }

    /**
     * @param blockState    Instance of {@link BlockState} to extract {@link Block} from.
     * @return              Namespaced name of the block material.
     */
    private static String getBlockRegistryName(BlockState blockState) {
        return getBlockRegistryName(blockState.getBlock());
    }

    /**
     * @param block         Instance of {@link Block} to find in {@link ForgeRegistries#BLOCKS}.
     * @return              Namespaced name of the block material.
     */
    private static String getBlockRegistryName(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).toString();
    }

    public static int getBlockFlags(boolean doBlockUpdates, boolean spawnDrops) {
        /*
            flags:
                * 1 will cause a block update.
                * 2 will send the change to clients.
                * 4 will prevent the block from being re-rendered.
                * 8 will force any re-renders to run on the main thread instead
                * 16 will prevent neighbor reactions (e.g. fences connecting, observers pulsing).
                * 32 will prevent neighbor reactions from spawning drops.
                * 64 will signify the block is being moved.
        */
        // construct flags
        return Block.UPDATE_CLIENTS | (doBlockUpdates ? Block.UPDATE_NEIGHBORS : (Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE)) | (spawnDrops ? 0 : Block.UPDATE_SUPPRESS_DROPS);
    }

    // function that converts a bunch of Property/Comparable pairs into String/String pairs
    private static final Function<Map.Entry<Property<?>, Comparable<?>>, Map.Entry<String, String>> propertyToStringPairFunction =
        new Function<>() {
            public Map.Entry<String, String> apply(@Nullable Map.Entry<Property<?>, Comparable<?>> element) {
                if (element == null) {
                    return null;
                } else {
                    Property<?> property = element.getKey();
                    return new ImmutablePair<>(property.getName(), this.valueToName(property, element.getValue()));
                }
            }

            private <T extends Comparable<T>> String valueToName(Property<T> property, Comparable<?> propertyValue) {
                return property.getName((T) propertyValue);
            }
        };

    /**
     * Copied from {@link net.minecraft.world.level.block.state.BlockBehaviour} class
     */
    private static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};

    private final class BlockPlacementInstruction {

        private final JsonObject inputData;

        private final CommandSourceStack commandSourceStack;

        public final BlockPos blockPos;
        public final ChunkPos chunkPos;
        private BlockState blockState;
        private CompoundTag blockNBT;

        public boolean isValid = true;
        private JsonObject returnValue;
        private boolean placementResult = false;

        /**
         * Data structure for each individual placement instruction
         *
         * @param placementInstructionInput     Json object that includes the block ID (required), xyz position (required),
         *                                      block state (optional) and block NBT data (optional) for the to be placed block.
         * @param commandSourceStack            Command source to be used as reference point if block position is relative.
         */
        BlockPlacementInstruction(JsonObject placementInstructionInput, CommandSourceStack commandSourceStack) {
            inputData = placementInstructionInput;
            this.commandSourceStack = commandSourceStack;
            // Parse block position x y z. Use the position of the command source (set with the URL query parameters) if not defined in
            // the block placement item JsonObject. Valid values may be any positive or negative integer and can use tilde or caret notation
            // (see: https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates).
            String posXString = inputData.has("x") ? inputData.get("x").getAsString() : String.valueOf(commandSourceStack.getPosition().x);
            String posYString = inputData.has("y") ? inputData.get("y").getAsString() : String.valueOf(commandSourceStack.getPosition().y);
            String posZString = inputData.has("z") ? inputData.get("z").getAsString() : String.valueOf(commandSourceStack.getPosition().z);
            BlockPos blockPos1 = null;
            try {
                blockPos1 = getBlockPosFromString(
                    posXString + " " + posYString + " " + posZString,
                    commandSourceStack
                );
                if (BuildArea.isOutsideBuildArea(blockPos1, withinBuildArea)) {
                    this.invalidate("position is outside build area " + inputData);
                }
            } catch (CommandSyntaxException e) {
                this.invalidate(e.getMessage());
            }
            blockPos = blockPos1;
            chunkPos = new ChunkPos(Objects.requireNonNull(blockPos1));
        }

        /**
         * Invalidates the placement instruction for when the input data cannot be parsed correctly.
         *
         * @param message   status message
         */
        private void invalidate(String message) {
            returnValue = instructionStatus(false, message);
            isValid = false;
        }

        /**
         * Parse block ID, block state string or JSON object into a {@code BlockState} and the SNBT string into a {@code CompoundTag}.
         */
        public void parse() {
            try {
                // Skip if block id is missing
                if (!inputData.has("id")) {
                    invalidate("block id is missing in " + inputData);
                    return;
                }
                String blockId = inputData.get("id").getAsString();

                // Check if JSON contains an JsonObject or string for block state.
                String blockStateString = "";
                if (inputData.has("state")) {
                    if (inputData.get("state").isJsonObject()) {
                        blockStateString = getBlockStateStringFromJSONObject(inputData.get("state").getAsJsonObject());
                    } else if (inputData.get("state").isJsonPrimitive()) {
                        blockStateString = inputData.get("state").getAsString();
                    }
                }

                // If data field is present in JsonObject serialize to to a string so it can be parsed to a CompoundTag to set as NBT block entity data
                // for this block placement.
                String blockNBTString = "";
                if (inputData.has("data") && inputData.get("data").isJsonPrimitive()) {
                    blockNBTString = inputData.get("data").getAsString();
                }

                // Pass block Id and block state string into a Stringreader with the the block state parser.
                BlockStateParser.BlockResult parsedBlockState = BlockStateParser.parseForBlock(
                    commandSourceStack.getLevel().holderLookup(Registries.BLOCK),
                    blockId + blockStateString + blockNBTString,
                    true
                );
                blockState = parsedBlockState.blockState();
                blockNBT = parsedBlockState.nbt();

            } catch (CommandSyntaxException e) {
                invalidate(e.getMessage());
            }
        }

        /**
         * Update {@code BlockState} of neighboring blocks that aren't part of the current list of block placement instructions to match their shape
         * with the to be placed blocks.
         * If block placement flags allow for updating neighbouring blocks, update the shape neighbouring blocks
         * in the west, east, north, south, down and up directions.
         * Algorithm based on the {@code updateFromNeighbourShapes} method in {@link net.minecraft.world.level.block.Block} with the
         * addition of it skipping all blocks that are also being placed within the same request.
         *
         * @param level     server level
         * @param flags     block update flags
         * @param blockPlacementInstructions    list of all placement instructions in the current PUT /blocks request
         */
        public void updateNeighborsBlocks(ServerLevel level, int flags, LinkedHashMap<BlockPos, BlockPlacementInstruction> blockPlacementInstructions) {
            if (!isValid) {
                return;
            }

            if ((flags & Block.UPDATE_NEIGHBORS) != 0) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                BlockState newBlockState = blockState;
                for (Direction direction : UPDATE_SHAPE_ORDER) {
                    mutableBlockPos.setWithOffset(blockPos, direction);
                    if (!blockPlacementInstructions.containsKey(mutableBlockPos)) {
                        newBlockState = newBlockState.updateShape(direction, level.getBlockState(mutableBlockPos), level, blockPos, mutableBlockPos);
                    }
                }
                if (newBlockState != null && !newBlockState.isAir()) {
                    blockState = newBlockState;
                }
            }
        }

        /**
         * Actually places the block in the level
         *
         * @param level     server level
         * @param flags     block update flags
         */
        public void setBlock(ServerLevel level, int flags) {
            if (!isValid) {
                return;
            }

            if (level.setBlock(blockPos, blockState, flags)) {
                // If block update flags allow for updating the shape of the block, perform a setPlaceBy action by a block place entity to "finalize"
                // the placement of the block. This is applicable for multi-part blocks that behave as one in-game, such as beds and doors.
                if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0) {
                    blockState.getBlock().setPlacedBy(level, blockPos, blockState, blockPlaceEntity, new ItemStack(blockState.getBlock().asItem()));
                }
                placementResult = true;
            } else {
                placementResult = false;
            }
        }

        /**
         * Updates placed block with new NBT data, if provided.
         *
         * @param level     server level
         * @param chunk     level chunk
         * @param flags     block update flags
         */
        public void setBlockNBT(ServerLevel level, LevelChunk chunk, int flags) {
            if (!isValid || blockNBT == null) {
                return;
            }

            // If existing block entity is different from the value of blockNBT,
            // overwrite existing block entity data with the new one, then notify
            // the level of this change to make the change visible in the world.
            BlockEntity existingBlockEntity = chunk.getExistingBlockEntity(blockPos);
            if (existingBlockEntity != null) {
                if (TagUtils.contains(existingBlockEntity.serializeNBT(), blockNBT)) {
                    return;
                }
                existingBlockEntity.deserializeNBT(blockNBT);
                if (
                    (flags & Block.UPDATE_CLIENTS) != 0 && (
                        !level.isClientSide || (flags & Block.UPDATE_INVISIBLE) == 0
                    ) && (
                        level.isClientSide || chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                    )
                ) {
                    level.sendBlockUpdated(blockPos, chunk.getBlockState(blockPos), blockState, flags);
                }
                placementResult = true;
            }
        }

        /**
         * @return Json Object representing the final status of this block placement instruction
         */
        public JsonObject getResult() {
            return Objects.requireNonNullElseGet(returnValue, () -> instructionStatus(placementResult));
        }

    }
}

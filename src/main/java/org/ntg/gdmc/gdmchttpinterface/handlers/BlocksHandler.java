package org.ntg.gdmc.gdmchttpinterface.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.ntg.gdmc.gdmchttpinterface.utils.BuildArea;
import org.ntg.gdmc.gdmchttpinterface.utils.TagUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.IntStream;

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

    // GET: Whether to include block state https://minecraft.wiki/w/Block_states
    private boolean includeState;

    // GET: Whether to include block entity data https://minecraft.wiki/w/Chunk_format#Block_entity_format
    private boolean includeData;

    // PUT: Defaults to true. If true, update neighbouring blocks after placement.
    private boolean doBlockUpdates;

    // PUT: Defaults to false. If true, block updates cause item drops after placement.
    private boolean spawnDrops;

    // PUT: Overrides both doBlockUpdates and spawnDrops if set. For more information see #getBlockFlags and
    // https://minecraft.wiki/w/Block_update
    private int customFlags; // -1 == no custom flags

    // PUT/GET: If true, constrain placement/getting blocks within the current build area.
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
        JsonArray inputList = parseJsonArray(requestBody);

        JsonArray returnValues = new JsonArray();

        // Create instance of CommandSourceStack to use as a point of origin for any relative positioned blocks.
        CommandSourceStack commandSourceStack = cmdSrc.withPosition(new Vec3(x, y, z));

        ServerLevel serverLevel = getServerLevel(dimension);

        int blockFlags = customFlags >= 0 ? customFlags : getBlockFlags(doBlockUpdates, spawnDrops);


        // Note the number of entries in this map may be smaller than inputList.size(), due to some input placement instructions being invalid or
        // due to there being multiple entries for the same block position.
        ConcurrentHashMap<Integer, PlacementInstructionFuturesRecord> placementInstructionsFuturesMap = new ConcurrentHashMap<>(inputList.size());
        // Map to hold parsed placement instructions, one indexed by the order it was submitted in so placement can be resolved in the right order if needed,
        // the other indexed by BlockPos to enable quick lookup of adjecent block positions for updating the block's shape.
        ConcurrentHashMap<Integer, PlacementInstructionRecord> parsedPlacementInstructionsIndexMap = new ConcurrentHashMap<>(inputList.size());
        ConcurrentHashMap<BlockPos, PlacementInstructionRecord> parsedPlacementInstructionsBlockPosMap = new ConcurrentHashMap<>(inputList.size());

        // Map for storing the resulting parsing error, placement failure or placement success of each placement instruction from the input.
        ConcurrentHashMap<Integer, JsonObject> placementResult = new ConcurrentHashMap<>(inputList.size());

        // Fill a map of records, each containing futures for parsing the BlockPos, BlockState and NBT CompoundTag data of a placement instruction.
        // Submit all of these futures to the cached thread pool, which will resolve these in an undetermined yet efficient way.
        // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html#newCachedThreadPool()
        ExecutorService executorService = Executors.newCachedThreadPool();
        IntStream.range(0, inputList.size()).parallel().forEach(index -> {
            JsonObject blockPlacementInput = inputList.get(index).getAsJsonObject();
            Future<BlockPos> blockPosFuture = executorService.submit(() -> getBlockPosFromJSON(blockPlacementInput, commandSourceStack));
            Future<BlockStateParser.BlockResult> blockStateParserFuture = executorService.submit(() -> getBlockStateFromJson(blockPlacementInput, commandSourceStack));
            placementInstructionsFuturesMap.put(index, new PlacementInstructionFuturesRecord(blockPosFuture, blockStateParserFuture));
        });
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        ConcurrentHashMap<ChunkPos, LevelChunk> chunkPosMap = new ConcurrentHashMap<>();
        IntStream.range(0, inputList.size()).parallel().forEach(index -> {
            PlacementInstructionFuturesRecord placementInstructionFuturesRecord = placementInstructionsFuturesMap.get(index);
            BlockPos blockPos;
            BlockState blockState;
            CompoundTag nbt;

            try {
                blockPos = placementInstructionFuturesRecord.posFuture.get();
                if (BuildArea.isOutsideBuildArea(blockPos, withinBuildArea)) {
                    placementResult.put(index, instructionStatus(false, "Position is outside build area!"));
                    return;
                }

                ChunkPos chunkPos = new ChunkPos(blockPos);
                if (!chunkPosMap.containsKey(chunkPos)) {
                    // Load the chunk data for the chunk pos of this block position.
                    // Loading the chunks before applying the NBT data helps with performance.
                    chunkPosMap.put(chunkPos, serverLevel.getChunk(chunkPos.x, chunkPos.z));
                }
            } catch (InterruptedException | ExecutionException | NullPointerException e) {
                placementResult.put(index, instructionStatus(false, e.getMessage()));
                return;
            }

            try {
                // Extract BlockState (cannot be null, will be caught during parsing) and NBT CompoundTag (can be null).
                BlockStateParser.BlockResult blockStateResult = placementInstructionFuturesRecord.blockStateFuture.get();
                blockState = blockStateResult.blockState();

                nbt = blockStateResult.nbt();
            } catch (InterruptedException | ExecutionException | NullPointerException e) {
                placementResult.put(index, instructionStatus(false, e.getMessage()));
                return;
            }

            PlacementInstructionRecord placementInstruction = new PlacementInstructionRecord(index, blockPos, blockState, nbt);
            parsedPlacementInstructionsIndexMap.put(index, placementInstruction);
            parsedPlacementInstructionsBlockPosMap.put(blockPos, placementInstruction);

        });

        // Allow for parallisation if blocks do not need to be updated, speeding up placement significantly.
        IntStream iterator = (blockFlags & Block.UPDATE_NEIGHBORS) == 0 ? IntStream.range(0, inputList.size()).parallel() : IntStream.range(0, inputList.size());
        iterator.forEach(index -> {
            PlacementInstructionRecord placementInstruction = parsedPlacementInstructionsIndexMap.get(index);
            if (placementInstruction == null) {
                return;
            }
            BlockPos blockPos = placementInstruction.blockPos;
            BlockState blockState = updateBlockShape(blockPos, placementInstruction.blockState, parsedPlacementInstructionsBlockPosMap, chunkPosMap, serverLevel, blockFlags);
            CompoundTag nbt = placementInstruction.nbt;

            boolean isBlockSet;
            try {
                isBlockSet = setBlock(blockPos, blockState, serverLevel, blockFlags);
            } catch (ExecutionException | InterruptedException e) {
                placementResult.put(index, instructionStatus(false, e.getMessage()));
                return;
            }

            if (nbt != null) {
                isBlockSet |= setBlockNBT(
                    blockPos,
                    nbt,
                    chunkPosMap.get(new ChunkPos(blockPos)),
                    blockState,
                    blockFlags
                );
            }
            placementResult.put(index, instructionStatus(isBlockSet));
        });

        // Gather placement/parsing results and put them back in the order the placement instructions were submitted.
        IntStream.range(0, inputList.size()).forEach(index -> returnValues.add(placementResult.get(index)));

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
        BoundingBox box;
        try {
            box = BuildArea.clampToBuildArea(createBoundingBox(x, y, z, dx, dy, dz), withinBuildArea);
        } catch (HttpException e) {
            // Return empty list if entire requested area is outside of build area (if withinBuildArea is true).
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
     * see: <a href="https://minecraft.wiki/w/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>
     *
     * @param str                       {@link String} which may or may not contain a valid block position coordinate.
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
     * Parse block position x y z.
     * Valid values may be any positive or negative integer and can use tilde or caret notation.
     * see: <a href="https://minecraft.wiki/w/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>
     *
     * @param json                      {@link JsonObject} which may or may not contain a valid block position coordinate.
     * @param commandSourceStack        Origin for relative coordinates.
     * @return Valid {@link BlockPos}.
     * @throws CommandSyntaxException   If input string cannot be parsed into a valid {@link BlockPos}.
     */
    private static BlockPos getBlockPosFromJSON(JsonObject json, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        String posXString = json.has("x") ? json.get("x").getAsString() : String.valueOf(commandSourceStack.getPosition().x);
        String posYString = json.has("y") ? json.get("y").getAsString() : String.valueOf(commandSourceStack.getPosition().y);
        String posZString = json.has("z") ? json.get("z").getAsString() : String.valueOf(commandSourceStack.getPosition().z);
        return getBlockPosFromString(
            posXString + " " + posYString + " " + posZString,
            commandSourceStack
        );
    }

    /**
     * Extract a {@link BlockStateParser.BlockResult}, containing the {@link BlockState} and NBT {@link CompoundTag} data, from the input {@link JsonObject}.
     *
     * @param json                      Input JSON object.
     * @param commandSourceStack        Origin point to resolve relative coordinates from. See <a href="https://minecraft.wiki/w/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>.
     * @return                          The resulting {@link BlockStateParser.BlockResult}.
     * @throws CommandSyntaxException   May be thrown if {@code "state"} or {@code "data"} field contain a syntax error.
     */
    private static BlockStateParser.BlockResult getBlockStateFromJson(JsonObject json, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        // Skip if block id is missing
        String blockId = json.get("id").getAsString();

        // Check if JSON contains an JsonObject or string for block state.
        String blockStateString = "";
        if (json.has("state")) {
            if (json.get("state").isJsonObject()) {
                blockStateString = getBlockStateStringFromJSONObject(json.get("state").getAsJsonObject());
            } else if (json.get("state").isJsonPrimitive()) {
                blockStateString = json.get("state").getAsString();
            }
        }

        // If data field is present in JsonObject serialize to to a string so it can be parsed to a CompoundTag to set as NBT block entity data
        // for this block placement.
        String blockNBTString = "";
        if (json.has("data") && json.get("data").isJsonPrimitive()) {
            blockNBTString = json.get("data").getAsString();
        }

        // Pass block Id and block state string into a Stringreader with the the block state parser.
	    return BlockStateParser.parseForBlock(
            commandSourceStack.getLevel().holderLookup(Registries.BLOCK),
            blockId + blockStateString + blockNBTString,
            true
        );
    }

    /**
     * @param json  Valid flat {@link JsonObject} of keys with primitive values (Strings, numbers, booleans)
     * @return      {@link String} which can be parsed by {@link BlockStateParser} and should be the same as the return value of {@link BlockState#toString()} of the {@link BlockState} resulting from that parser.
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

    /**
     * Actually places the block in the level.
     *
     * @param blockPos                  Target position of the to-be-placed block.
     * @param blockState                {@link BlockState} of the to-be-placed block. This also contains the block ID (eg. {@code minecraft:stone} {@code minecraft:gold_block}).
     * @param level                     Level in which the block will be placed.
     * @param flags                     Block update flags (see {@link #getBlockFlags}).
     * @return                          False if block at target position has the same {@link BlockState} as the input, if the target positions was outside of the world bounds or if it couldn't be placed for some other reason.
     */
    private boolean setBlock(BlockPos blockPos, BlockState blockState, ServerLevel level, int flags) throws ExecutionException, InterruptedException {
        // Submit setBlock function call to the Minecraft server thread, allowing Minecraft to schedule this call, preventing interruptions of the
        // server thread, resulting in faster placement overall and much less stuttering in-game when placing lots of blocks.
        CompletableFuture<Boolean> isBlockSetFuture = mcServer.submit(() -> level.setBlock(blockPos, blockState, flags));
        boolean isBlockSet = isBlockSetFuture.get();

        // If block update flags allow for updating the shape of the block, perform a setPlaceBy action by a block place entity to "finalize"
        // the placement of the block. This is applicable for multi-part blocks that behave as one in-game, such as beds and doors.
        if (isBlockSet && (flags & Block.UPDATE_KNOWN_SHAPE) == 0) {
            blockState.getBlock().setPlacedBy(level, blockPos, blockState, blockPlaceEntity, new ItemStack(blockState.getBlock().asItem()));
        }
        return isBlockSet;
    }

    /**
     * Applies NBT data to block in the level.
     *
     * @param blockPos      Absolute position of the block.
     * @param blockNBT      NBT data to apply to the block.
     * @param chunk         Chunk of the level that the target block can be found it.
     * @param blockState    Original block state.
     * @param flags         Block update flags (see {@link #getBlockFlags(boolean, boolean)}).
     * @return              true if NBT of the block in the level got changed.
     */
    private static boolean setBlockNBT(BlockPos blockPos, @Nullable CompoundTag blockNBT, LevelChunk chunk, BlockState blockState, int flags) {
        if (blockNBT == null) {
            return false;
        }
        // If existing block entity is different from the value of blockNBT,
        // overwrite existing block entity data with the new one, then notify
        // the level of this change to make the change visible in the world.
        BlockEntity existingBlockEntity = chunk.getExistingBlockEntity(blockPos);
        if (existingBlockEntity != null) {
            // If the NBT data on the existing block is the same as the NBT data
            // from the input, do not bother applying the input NBT data.
            if (TagUtils.contains(existingBlockEntity.serializeNBT(), blockNBT)) {
                return false;
            }
            existingBlockEntity.deserializeNBT(blockNBT);
            if (
                (flags & Block.UPDATE_CLIENTS) != 0 && (
                    !chunk.getLevel().isClientSide || (flags & Block.UPDATE_INVISIBLE) == 0
                ) && (
                    chunk.getLevel().isClientSide || chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                )
            ) {
                chunk.getLevel().sendBlockUpdated(blockPos, chunk.getBlockState(blockPos), blockState, flags);
            }
            return true;
        }
        return false;
    }

    /**
     * Order of directions to update block shape with. Copied from {@link net.minecraft.world.level.block.state.BlockBehaviour}.
     */
    private static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};

    /**
     * Changes the shape of the block depending on the blocks horizontally adjacent to it.
     * <p>
     * The "shape" of the block is defined in its {@link BlockState}. For example, the BlockState of a fence block
     * that has a stone block east from it will be updated to have the property {@code east=true}, making the fence
     * join up with the stone block.
     * <p>
     * Shape of the block will remain unchanged if it has no adjecent blocks that influence its shape,
     * if the final shape resolves to air, or if the block placement flags ({@link #getBlockFlags(boolean, boolean)}
     * do not allow for changing the block's shape.
     *
     * @param inputBlockPos             Position of block to change the shape of.
     * @param inputBlockState           Original {@link BlockState}.
     * @param placementInstructionsMap  Other placement instructions to find neighbouring blocks in.
     * @param chunkMap                  Cached chunks to find neighbouring blocks in.
     * @param level                     Level to find neighbouring blocks in.
     * @param flags                     Block placement flags (see {@link #getBlockFlags(boolean, boolean)}.
     * @return                          The updated {@link BlockState}.
     */
    private static BlockState updateBlockShape(BlockPos inputBlockPos, BlockState inputBlockState, ConcurrentHashMap<BlockPos, PlacementInstructionRecord> placementInstructionsMap, ConcurrentHashMap<ChunkPos, LevelChunk> chunkMap, ServerLevel level, int flags) {
        if ((flags & Block.UPDATE_NEIGHBORS) == 0) {
            return inputBlockState;
        }
        // If block placement flags allow for updating the block based on neighbouring blocks, do so in the north, west,
        // south, east, up, down directions. Use block states from other placement instruction items to ensure the shape
        // comforms when the placement instructions are resolved. If no placement instruction is present get the
        // neighbouring BlockState from any preloaded chunks. If null, load the block state from the level.
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockState newBlockState = inputBlockState;
        for (Direction direction : UPDATE_SHAPE_ORDER) {
            mutableBlockPos.setWithOffset(inputBlockPos, direction);

            PlacementInstructionRecord otherPlacementInstruction = placementInstructionsMap.get(mutableBlockPos);
            if (otherPlacementInstruction != null) {
                newBlockState = newBlockState.updateShape(direction, otherPlacementInstruction.blockState, level, inputBlockPos, mutableBlockPos);
                continue;
            }
            LevelChunk chunk = chunkMap.get(new ChunkPos(mutableBlockPos));
            if (chunk != null) {
                newBlockState = newBlockState.updateShape(direction, chunk.getBlockState(mutableBlockPos), level, inputBlockPos, mutableBlockPos);
                continue;
            }
            newBlockState = newBlockState.updateShape(direction, level.getBlockState(mutableBlockPos), level, inputBlockPos, mutableBlockPos);
        }
        if (newBlockState.isAir()) {
            return inputBlockState;
        }
        return newBlockState;
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

    /**
     * Record to hold parsing Futures (tasks, promises, whatever you would like to call them) that belong to the same block placement instruction.
     *
     * @param posFuture         Task that parses a JSON object into a {@link BlockPos}.
     * @param blockStateFuture  Task that parses a JSON object into a {@link BlockStateParser.BlockResult}, containing the {@link BlockState} and {@link CompoundTag}.
     */
    private record PlacementInstructionFuturesRecord(Future<BlockPos> posFuture, Future<BlockStateParser.BlockResult> blockStateFuture) {}

    /**
     * Record to hold the parsed block placement instruction.
     *
     * @param placementOrder    Order in which the placement instructions was submitted.
     * @param blockPos          Absolute block position of the to-be-placed block.
     * @param blockState        {@link BlockState} of the to-be-placed block.
     * @param nbt               NBT data of the to-be-placed block. May be null if none was provided in the input data.
     */
    private record PlacementInstructionRecord(int placementOrder, BlockPos blockPos, BlockState blockState, @Nullable CompoundTag nbt) {}

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

}

package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
        int blockFlags = customFlags >= 0 ? customFlags : getBlockFlags(doBlockUpdates, spawnDrops);

        // Create instance of CommandSourceStack to use as a point of origin for any relative positioned blocks.
        CommandSourceStack commandSourceStack = cmdSrc.withPosition(new Vec3(x, y, z));

        JsonArray returnValues = new JsonArray();

        JsonArray blockPlacementList;
        try {
            blockPlacementList = JsonParser.parseReader(new InputStreamReader(requestBody)).getAsJsonArray();
        } catch (JsonSyntaxException jsonSyntaxException) {
            throw new HttpException("Malformed JSON: " + jsonSyntaxException.getMessage(), 400);
        }

        for (JsonElement blockPlacement : blockPlacementList) {
            JsonObject blockPlacementItem = blockPlacement.getAsJsonObject();
            try {

                // Parse block position x y z. Use the position of the command source (set with the URL query parameters) if not defined in
                // the block placement item JsonObject. Valid values may be any positive or negative integer and can use tilde or caret notation
                // (see: https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates).
                String posXString = blockPlacementItem.has("x") ? blockPlacementItem.get("x").getAsString() : String.valueOf(x);
                String posYString = blockPlacementItem.has("y") ? blockPlacementItem.get("y").getAsString() : String.valueOf(y);
                String posZString = blockPlacementItem.has("z") ? blockPlacementItem.get("z").getAsString() : String.valueOf(z);
                BlockPos blockPos = getBlockPosFromString(
                    "%s %s %s".formatted(posXString, posYString, posZString),
                    commandSourceStack
                );

                // Skip if block id is missing
                if (!blockPlacementItem.has("id")) {
                    returnValues.add(instructionStatus(false, "block id is missing in " + blockPlacement));
                    continue;
                }
                String blockId = blockPlacementItem.get("id").getAsString();

                // Check if JSON contains an JsonObject or string for block state. Use an empty block state string ("[]") if nothing suitable is found.
                String blockStateString = "[]";
                if (blockPlacementItem.has("state")) {
                    if (blockPlacementItem.get("state").isJsonObject()) {
                        blockStateString = getBlockStateStringFromJSONObject(blockPlacementItem.get("state").getAsJsonObject());
                    } else if (blockPlacementItem.get("state").isJsonPrimitive()) {
                        blockStateString = blockPlacementItem.get("state").getAsString();
                    }
                }

                // Pass block Id and block state string into a Stringreader with the the block state parser.
                BlockStateParser.BlockResult parsedBlockState = BlockStateParser.parseForBlock(
                    getBlockRegisteryLookup(commandSourceStack),
                    new StringReader(blockId + blockStateString),
                    true
                );
                BlockState blockState = parsedBlockState.blockState();

                // If data field is present in JsonObject serialize to to a string so it can be parsed to a CompoundTag to set as NBT block entity data
                // for this block placement.
                CompoundTag compoundTag = null;
                if (blockPlacementItem.has("data") && blockPlacementItem.get("data").isJsonPrimitive()) {
                    compoundTag = TagParser.parseTag(blockPlacementItem.get("data").getAsString());
                }

                // Attempt to place block in the world.
                boolean isSuccess = setBlock(blockPos, blockState, compoundTag, blockFlags) == 1;
                returnValues.add(instructionStatus(isSuccess));

            } catch (CommandSyntaxException e) {
                returnValues.add(instructionStatus(false, e.getMessage()));
            }
        }

        return returnValues;
    }

    /**
     * Get information on one of more blocks in the world.
     *
     * @return list of block information
     */
    private JsonArray getBlocksHandler() {

        // Calculate boundaries of area of blocks to gather information on.
        int xOffset = x + dx;
        int xMin = Math.min(x, xOffset);
        int xMax = Math.max(x, xOffset);

        int yOffset = y + dy;
        int yMin = Math.min(y, yOffset);
        int yMax = Math.max(y, yOffset);

        int zOffset = z + dz;
        int zMin = Math.min(z, zOffset);
        int zMax = Math.max(z, zOffset);

        // Create a JsonArray with JsonObject, each contain a key-value pair for
        // the x, y, z position, the block ID, the block state (if requested and available)
        // and the block entity data (if requested and available).
        JsonArray jsonArray = new JsonArray();
        for (int rangeX = xMin; rangeX < xMax; rangeX++) {
            for (int rangeY = yMin; rangeY < yMax; rangeY++) {
                for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
                    BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
                    String blockId = getBlockAsStr(blockPos);
                    JsonObject json = new JsonObject();
                    json.addProperty("id", blockId);
                    json.addProperty("x", rangeX);
                    json.addProperty("y", rangeY);
                    json.addProperty("z", rangeZ);
                    if (includeState) {
                        json.add("state", getBlockStateAsJsonObject(blockPos));
                    }
                    if (includeData) {
                        json.addProperty("data", getBlockDataAsStr(blockPos));
                    }
                    jsonArray.add(json);
                }
            }
        }
        return jsonArray;
    }

    private BlockState getBlockStateAtPosition(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);
        return serverLevel.getBlockState(pos);
    }

    /**
     * Parse block position x y z.
     * Valid values may be any positive or negative integer and can use tilde or caret notation.
     * see: <a href="https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>
     *
     * @param s                         {@code String} which may or may not contain a valid block position coordinate.
     * @param commandSourceStack        Origin for relative coordinates.
     * @return Valid {@link BlockPos}.
     * @throws CommandSyntaxException   If input string cannot be parsed into a valid {@link BlockPos}.
     */
    private static BlockPos getBlockPosFromString(String s, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        return getBlockPosFromString(new StringReader(s), commandSourceStack);
    }

    /**
     * Parse block position x y z.
     * Valid values may be any positive or negative integer and can use tilde or caret notation.
     * see: <a href="https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates">Relative World Coordinates - Minecraft Wiki</a>
     *
     * @param blockPosStringReader      {@code StringReader} which may or may not contain a valid block position coordinate.
     * @param commandSourceStack        Origin for relative coordinates.
     * @return Valid {@link BlockPos}.
     * @throws CommandSyntaxException   If input string reader cannot be parsed into a valid {@link BlockPos}.
     */
    private static BlockPos getBlockPosFromString(StringReader blockPosStringReader, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        Coordinates coordinates = BlockPosArgument.blockPos().parse(blockPosStringReader);
        blockPosStringReader.skip();
        return coordinates.getBlockPos(commandSourceStack);
    }

    /**
     * @param json  Valid flat {@link JsonObject} of keys with primitive values (Strings, numbers, booleans)
     * @return      {@code String} which can be parsed by {@link BlockStateParser} and should be the same as the return value of {@link BlockState#toString()} of the {@link BlockState} resulting from that parser.
     */
    private static String getBlockStateStringFromJSONObject(JsonObject json) {
        ArrayList<String> blockStateList = new ArrayList<>();
        for (Map.Entry<String, JsonElement> element : json.entrySet()) {
            blockStateList.add(element.getKey() + "=" + element.getValue());
        }
        return '[' + String.join(",", blockStateList) + ']';
    }

    /**
     * @param pos                   Position in the world the block should be placed.
     * @param blockState            Contains both the state as well as the material of the block.
     * @param blockEntityData       Optional tag of NBT data to be associated with the block (eg. contents of a chest).
     * @param flags                 Block placement flags (see {@link #getBlockFlags(boolean, boolean)} and {@link Block} for more information).
     * @return                      return 1 if block has been placed or 0 if it couldn't be placed at the given location.
     */
    private int setBlock(BlockPos pos, BlockState blockState, CompoundTag blockEntityData, int flags) {
        ServerLevel serverLevel = getServerLevel(dimension);

        BlockEntity blockEntitytoClear = serverLevel.getBlockEntity(pos);
        Clearable.tryClear(blockEntitytoClear);

        if (serverLevel.setBlock(pos, blockState, flags)) {
            if (blockEntityData != null) {
                BlockEntity existingBlockEntity = serverLevel.getExistingBlockEntity(pos);
                if (existingBlockEntity != null) {
                    existingBlockEntity.deserializeNBT(blockEntityData);
                }
            }

            // If block placement flags allow for updating the shape of placed blocks, resolve placing the block as if it was placed by a player.
            // This is applicable to certain blocks that form an object larger than just a single block, such as doors and beds.
            if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0) {
                blockState.getBlock().setPlacedBy(serverLevel, pos, blockState, blockPlaceEntity, new ItemStack(blockState.getBlock().asItem()));
            }

            // If block placement flags allow for updating neighbouring blocks, update the shape neighbouring blocks
            // in the north, west, south, east, up, down directions.
            if ((flags & Block.UPDATE_NEIGHBORS) != 0) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbourPosition = pos.relative(direction);
                    BlockState neighbourBlockState = serverLevel.getBlockState(neighbourPosition);
                    neighbourBlockState.updateNeighbourShapes(serverLevel, neighbourPosition, flags);
                }
            }
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * @param pos   Position of block in the world.
     * @return      Namespaced name of the block material.
     */
    private String getBlockAsStr(BlockPos pos) {
        BlockState bs = getBlockStateAtPosition(pos);
        return Objects.requireNonNull(getBlockRegistryName(bs));
    }

    /**
     * @param pos   Position of block in the world.
     * @return      {@link JsonObject} containing the block state data of the block at the given position.
     */
    private JsonObject getBlockStateAsJsonObject(BlockPos pos) {
        BlockState bs = getBlockStateAtPosition(pos);
        JsonObject stateJsonObject = new JsonObject();
        bs.getValues().entrySet().stream().map(propertyToStringPairFunction).filter(Objects::nonNull).forEach(pair -> stateJsonObject.add(pair.getKey(), new JsonPrimitive(pair.getValue())));
        return stateJsonObject;
    }

    /**
     * @param pos   Position of block in the world.
     * @return      {@link String} containing the block entity data of the block at the given position.
     */
    private String getBlockDataAsStr(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);
        String str = "{}";
        BlockEntity blockEntity = serverLevel.getExistingBlockEntity(pos);
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
    public static String getBlockRegistryName(BlockState blockState) {
        return getBlockRegistryName(blockState.getBlock());
    }

    /**
     * @param block         Instance of {@link Block} to find in {@link ForgeRegistries#BLOCKS}.
     * @return              Namespaced name of the block material.
     */
    public static String getBlockRegistryName(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).toString();
    }

    public static HolderLookup<Block> getBlockRegisteryLookup(CommandSourceStack commandSourceStack) {
      return new CommandBuildContext(commandSourceStack.registryAccess()).holderLookup(Registry.BLOCK_REGISTRY);
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
}

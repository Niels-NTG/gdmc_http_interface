package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


public class BlocksHandler extends HandlerBase {

    private final CommandSourceStack cmdSrc;

    private String dimension;

    public BlocksHandler(MinecraftServer mcServer) {
        super(mcServer);
        cmdSrc = createCommandSource("GDMC-BlockHandler", mcServer, dimension);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        // query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());
        int x;
        int y;
        int z;
        int dx;
        int dy;
        int dz;
        boolean includeData;
        boolean includeState;
        boolean doBlockUpdates;
        boolean spawnDrops;
        int customFlags; // -1 == no custom flags

        try {
            x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
            y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
            z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

            dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
            dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

            includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));
            includeState = Boolean.parseBoolean(queryParams.getOrDefault("includeState", "false"));

            doBlockUpdates = Boolean.parseBoolean(queryParams.getOrDefault("doBlockUpdates", "true"));
            spawnDrops = Boolean.parseBoolean(queryParams.getOrDefault("spawnDrops", "false"));
            customFlags = Integer.parseInt(queryParams.getOrDefault("customFlags", "-1"), 2);

            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HttpException(message, 400);
        }

        // if content type is application/json use that otherwise return text
        Headers requestHeaders = httpExchange.getRequestHeaders();
        String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
        boolean returnJson = hasJsonTypeInHeader(acceptHeader);

        // construct response
        String method = httpExchange.getRequestMethod().toLowerCase();
        String responseString;

        if (method.equals("put")) {
            String contentTypeHeader = getHeader(requestHeaders,"Content-Type", "*/*");
            boolean parseRequestAsJson = hasJsonTypeInHeader(contentTypeHeader);

            int blockFlags = customFlags >= 0 ? customFlags : getBlockFlags(doBlockUpdates, spawnDrops);

            CommandSourceStack commandSourceStack = cmdSrc.withPosition(new Vec3(x, y, z));

            List<String> returnValues = new LinkedList<>();

            InputStream bodyStream = httpExchange.getRequestBody();

            if (parseRequestAsJson) {
                JsonArray blockPlacementList = JsonParser.parseReader(new InputStreamReader(bodyStream)).getAsJsonArray();
                for (JsonElement blockPlacement : blockPlacementList) {
                    String returnValue;
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
                            returnValues.add("block id is missing in " + blockPlacement);
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
                        HolderLookup<Block> blockStateArgumetBlocks = new CommandBuildContext(commandSourceStack.registryAccess()).holderLookup(Registry.BLOCK_REGISTRY);
                        BlockStateParser.BlockResult parsedBlockState = BlockStateParser.parseForBlock(blockStateArgumetBlocks, new StringReader(
                             blockId + blockStateString
                        ), true);
                        BlockState blockState = parsedBlockState.blockState();

                        // If data field is present in JsonObject serialize to to a string so it can be parsed to a CompoundTag to set as NBT block entity data
                        // for this block placement.
                        CompoundTag compoundTag = null;
                        if (blockPlacementItem.has("data")) {
                            compoundTag = TagParser.parseTag(blockPlacementItem.get("data").toString());
                        }

                        // Attempt to place block in the world.
                        returnValue = setBlock(blockPos, blockState, compoundTag, blockFlags) + "";

                    } catch (CommandSyntaxException e) {
                        returnValue = e.getMessage();
                    }

                    returnValues.add(returnValue);
                }

            } else {
                List<String> body = new BufferedReader(new InputStreamReader(bodyStream))
                    .lines().toList();

                for (String line : body) {
                    String returnValue;
                    try {
                        StringReader sr = new StringReader(line);
                        BlockPos blockPos = getBlockPosFromString(sr, commandSourceStack);

                        HolderLookup<Block> blockStateArgumetBlocks = new CommandBuildContext(commandSourceStack.registryAccess()).holderLookup(Registry.BLOCK_REGISTRY);
                        BlockStateParser.BlockResult parsedBlockState = BlockStateParser.parseForBlock(blockStateArgumetBlocks, sr, true);
                        BlockState blockState = parsedBlockState.blockState();
                        CompoundTag compoundTag = parsedBlockState.nbt();

                        returnValue = setBlock(blockPos, blockState, compoundTag, blockFlags) + "";

                    } catch (CommandSyntaxException e) {
                        returnValue = e.getMessage();
                    }
                    returnValues.add(returnValue);
                }
            }

            // Set response as a list of "1" (block was placed), "0" (block was not placed) or an exception string if something went wrong placing the block.
            if (returnJson) {
                responseString = new Gson().toJson(returnValues);
            } else {
                responseString = String.join("\n", returnValues);
            }
        } else if (method.equals("get")) {
            int xOffset = x + dx;
            int xMin = Math.min(x, xOffset);
            int xMax = Math.max(x, xOffset);

            int yOffset = y + dy;
            int yMin = Math.min(y, yOffset);
            int yMax = Math.max(y, yOffset);

            int zOffset = z + dz;
            int zMin = Math.min(z, zOffset);
            int zMax = Math.max(z, zOffset);

            if (returnJson) {
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
                                json.add("data", getBlockDataAsJsonObject(blockPos));
                            }
                            jsonArray.add(json);
                        }
                    }
                }
                responseString = new Gson().toJson(jsonArray);
            } else {
                ArrayList<String> responseList = new ArrayList<>();
                for (int rangeX = xMin; rangeX < xMax; rangeX++) {
                    for (int rangeY = yMin; rangeY < yMax; rangeY++) {
                        for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
                            BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
                            String listItem = rangeX + " " + rangeY + " " + rangeZ + " " + getBlockAsStr(blockPos);
                            if (includeState) {
                                listItem += getBlockStateAsStr(blockPos);
                            }
                            if (includeData) {
                                listItem += getBlockDataAsStr(blockPos);
                            }
                            responseList.add(listItem);
                        }
                    }
                }
                responseString = String.join("\n", responseList);
            }
        } else {
            throw new HttpException("Method not allowed. Only PUT and GET requests are supported.", 405);
        }

        //headers
        Headers headers = httpExchange.getResponseHeaders();
        addDefaultHeaders(headers);

        if (returnJson) {
            headers.add("Content-Type", "application/json; charset=UTF-8");
        } else {
            headers.add("Content-Type", "text/plain; charset=UTF-8");
        }

        resolveRequest(httpExchange, responseString);
    }

    private BlockState getBlockStateAtPosition(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);
        return serverLevel.getBlockState(pos);
    }

    private static BlockPos getBlockPosFromString(String s, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        return getBlockPosFromString(new StringReader(s), commandSourceStack);
    }

    private static BlockPos getBlockPosFromString(StringReader blockPosStringReader, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
        Coordinates coordinates = BlockPosArgument.blockPos().parse(blockPosStringReader);
        blockPosStringReader.skip();
        return coordinates.getBlockPos(commandSourceStack);
    }

    private static String getBlockStateStringFromJSONObject(JsonObject json) {
        LinkedList<String> blockStateList = new LinkedList<>();
        for (Map.Entry<String, JsonElement> element : json.entrySet()) {
            blockStateList.add(element.getKey() + "=" + element.getValue());
        }
        return '[' + String.join(",", blockStateList) + ']';
    }

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

            // If block placement flags allow for updating neighbouring blocks, update the shape neighbouring blocks
            // in the north, west, south, east, up, down directions.
            if ((flags & Block.UPDATE_NEIGHBORS) != 0 && (flags & Block.UPDATE_KNOWN_SHAPE) == 0) {
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

    private String getBlockAsStr(BlockPos pos) {
        BlockState bs = getBlockStateAtPosition(pos);
        return Objects.requireNonNull(getBlockRegistryName(bs));
    }

    private JsonObject getBlockStateAsJsonObject(BlockPos pos) {
        BlockState bs = getBlockStateAtPosition(pos);

        JsonObject stateJsonObject = new JsonObject();
        bs.getValues().entrySet().stream().map(propertyToStringPairFunction).filter(Objects::nonNull).forEach(pair -> stateJsonObject.add(pair.getKey(), new JsonPrimitive(pair.getValue())));
        return stateJsonObject;
    }

    private String getBlockStateAsStr(BlockPos pos) {
        BlockState bs = getBlockStateAtPosition(pos);

        return '[' +
            bs.getValues().entrySet().stream().map(propertyToStringFunction).collect(Collectors.joining(",")) +
        ']';
    }

    private JsonObject getBlockDataAsJsonObject(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);
        JsonObject dataJsonObject = new JsonObject();
        BlockEntity blockEntity = serverLevel.getExistingBlockEntity(pos);
        if (blockEntity != null) {
            CompoundTag tags = blockEntity.saveWithoutMetadata();
            String tagsAsJsonString = (new JsonTagVisitor()).visit(tags);
            JsonObject tagsAsJsonObject = JsonParser.parseString(tagsAsJsonString).getAsJsonObject();
            if (tagsAsJsonObject != null) {
                return tagsAsJsonObject;
            }
        }
        return dataJsonObject;
    }

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

    public static String getBlockRegistryName(BlockState blockState) {
        return getBlockRegistryName(blockState.getBlock());
    }
    public static String getBlockRegistryName(Block block) {
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

    // function that converts a bunch of Property/Comparable pairs into strings that look like 'property=value'
    private static final Function<Map.Entry<Property<?>, Comparable<?>>, String> propertyToStringFunction =
        new Function<>() {
            public String apply(@Nullable Map.Entry<Property<?>, Comparable<?>> element) {
                if (element == null) {
                    return "<NULL>";
                } else {
                    Property<?> property = element.getKey();
                    return property.getName() + "=" + this.valueToName(property, element.getValue());
                }
            }

            private <T extends Comparable<T>> String valueToName(Property<T> property, Comparable<?> propertyValue) {
                return property.getName((T) propertyValue);
            }
        };

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

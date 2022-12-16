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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
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
            throw new HandlerBase.HttpException(message, 400);
        }

        // if content type is application/json use that otherwise return text
        Headers reqestHeaders = httpExchange.getRequestHeaders();
        String contentType = getHeader(reqestHeaders, "Accept", "*/*");
        boolean returnJson = contentType.equals("application/json") || contentType.equals("text/json");

        // construct response
        String method = httpExchange.getRequestMethod().toLowerCase();
        String responseString;

        if (method.equals("put")) {
            InputStream bodyStream = httpExchange.getRequestBody();
            List<String> body = new BufferedReader(new InputStreamReader(bodyStream))
                    .lines().toList();

            List<String> returnValues = new LinkedList<>();

            int blockFlags = customFlags >= 0? customFlags : getBlockFlags(doBlockUpdates, spawnDrops);

            CommandSourceStack commandSourceStack = cmdSrc.withPosition(new Vec3(x, y, z));

            for(String line : body) {
                String returnValue;
                try {
                    StringReader sr = new StringReader(line);
                    Coordinates li = null;
                    try {
                        li = BlockPosArgument.blockPos().parse(sr);
                        sr.skip();
                    } catch (CommandSyntaxException e) {
                        sr = new StringReader(line); // TODO maybe delete this
                    }

                    int xx, yy, zz;
                    if (li != null) {
                        xx = (int)Math.round(li.getPosition(commandSourceStack).x);
                        yy = (int)Math.round(li.getPosition(commandSourceStack).y);
                        zz = (int)Math.round(li.getPosition(commandSourceStack).z);
                    } else {
                        xx = x;
                        yy = y;
                        zz = z;
                    }
                    BlockPos blockPos = new BlockPos(xx, yy, zz);

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
            if (returnJson) {
                JsonObject json = new JsonObject();
                JsonArray resultsArray = new JsonArray();

                for(String s : returnValues) {
                    resultsArray.add(s);
                }

                json.add("results", resultsArray);
                responseString = new Gson().toJson(json);
            } else {
                responseString = String.join("\n", returnValues);
            }
        } else if (method.equals("get")) {
            if (returnJson) {
                JsonArray jsonArray = new JsonArray();
                for (int rangeX = x; rangeX < x + dx; rangeX++) {
                    for (int rangeY = y; rangeY < y + dy; rangeY++) {
                        for (int rangeZ = z; rangeZ < z + dz; rangeZ++) {
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
                for (int rangeX = x; rangeX < x + dx; rangeX++) {
                    for (int rangeY = y; rangeY < y + dy; rangeY++) {
                        for (int rangeZ = z; rangeZ < z + dz; rangeZ++) {
                            BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
                            String listItem = rangeX + ' ' + rangeY + ' ' + rangeZ + ' ' + getBlockAsStr(blockPos);
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
            throw new HandlerBase.HttpException("Method not allowed. Only PUT and GET requests are supported.", 405);
        }

        //headers
        Headers headers = httpExchange.getResponseHeaders();
        addDefaultHeaders(headers);

        if(returnJson) {
            headers.add("Content-Type", "application/json; charset=UTF-8");
        } else {
            headers.add("Content-Type", "text/plain; charset=UTF-8");
        }

        resolveRequest(httpExchange, responseString);
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
            return 1;
        } else {
            return 0;
        }
    }

    public int getBlockFlags(boolean doBlockUpdates, boolean spawnDrops) {
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
        return 2 | ( doBlockUpdates? 1 : (32 | 16) ) | ( spawnDrops? 0 : 32 );
    }

    private String getBlockAsStr(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);

        BlockState bs = serverLevel.getBlockState(pos);
        return Objects.requireNonNull(getBlockRegistryName(bs));
    }

    private JsonObject getBlockStateAsJsonObject(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);

        BlockState bs = serverLevel.getBlockState(pos);

        JsonObject stateJsonObject = new JsonObject();

        bs.getValues().entrySet().stream().map(propertyToStringPairFunction).filter(Objects::nonNull).forEach(pair -> stateJsonObject.add(pair.getKey(), new JsonPrimitive(pair.getValue())));
        return stateJsonObject;
    }

    private String getBlockStateAsStr(BlockPos pos) {
        ServerLevel serverLevel = getServerLevel(dimension);

        BlockState bs = serverLevel.getBlockState(pos);

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

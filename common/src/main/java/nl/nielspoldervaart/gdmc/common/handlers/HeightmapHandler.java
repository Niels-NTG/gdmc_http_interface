package nl.nielspoldervaart.gdmc.common.handlers;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;
import nl.nielspoldervaart.gdmc.common.utils.CustomHeightmap;
import com.google.common.base.Enums;
import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class HeightmapHandler extends HandlerBase {

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        if (!httpExchange.getRequestMethod().equalsIgnoreCase("get")) {
            throw new HttpException("Method not allowed. Only GET and POST requests are supported.", 405);
        }

        // Get query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // Get comma-separated list of block IDs, block tag keys or fluid tag keys to use for custom heightmap definition.
        // https://minecraft.wiki/w/Java_Edition_data_values#Blocks
        // https://minecraft.wiki/w/Tag#Block_tags_2
        // https://minecraft.wiki/w/Tag#Fluid_tags
        String customTransparentBlocksString = queryParams.getOrDefault("blocks", "");
        Stream<String> customTransparentBlocksList = customTransparentBlocksString.isBlank() ? null : Arrays.stream(customTransparentBlocksString.split(","));

        // Get heightmap preset type argument. Default to WORLD_SURFACE as the default type.
        String heightmapType = queryParams.getOrDefault("type", "WORLD_SURFACE");

        // The dimension to get the heightmap data from.
        String dimension = queryParams.getOrDefault("dimension", null);

        ServerLevel level = getServerLevel(dimension);

        Optional<Integer> yMin = Optional.empty();
        Optional<Integer> yMax = Optional.empty();
        String yBoundsInput = queryParams.getOrDefault("yBounds", "");
        if (!yBoundsInput.isBlank() && customTransparentBlocksList != null) {
	        try {
                MinMaxBounds.Ints yBounds = RangeArgument.intRange().parse(new StringReader(yBoundsInput));
                #if (MC_VER == MC_1_19_2)
                    yMin = Optional.ofNullable(yBounds.getMin());
                    yMax = Optional.ofNullable(yBounds.getMax());
                #else
                    yMin = yBounds.min();
                    yMax = yBounds.max();
                #endif
                if (yMin.isPresent() && yMax.isPresent() && yMin.get() - yMax.get() == 0) {
                    throw new HttpException("yBounds should span more than 0", 400);
                }
            } catch (CommandSyntaxException e) {
                throw new HttpException("yBounds formatted incorrectly: " + e.getMessage(), 400);
	        }
        }

        // Preset heightmap type parameter is ignored if custom block list is not empty.
        int[][] heightmap = customTransparentBlocksList != null ?
            getHeightmap(level, dimension, yMin, yMax, customTransparentBlocksList) :
            getHeightmap(level, yMin, yMax, heightmapType);

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }

    private int[][] getHeightmap(ServerLevel serverlevel, String dimension, Optional<Integer> yMin, Optional<Integer> yMax, Stream<String> blockList) {
        CommandSourceStack commandSourceStack = createCommandSource(
            "GDMC-HeightmapHandler",
            dimension
        );

        ArrayList<BlockState> blockStateList = new ArrayList<>();
        ArrayList<String> blockTagKeyList = new ArrayList<>();
        blockList.parallel().forEach(blockString -> {
            try {
                if (blockString.startsWith("#")) {
                    blockString = formatBlockTagKeyLocation(blockString);
                    if (!isExistingBlockTagKey(blockString, commandSourceStack)) {
                        throw new HttpException("Invalid block tag key: " + blockString, 400);
                    }
                    blockTagKeyList.add(blockString);
                } else {
                    BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(
                        BlocksHandler.getBlockRegistryLookup(commandSourceStack),
                        new StringReader(blockString),
                        true
                    );
                    blockStateList.add(blockResult.blockState());
                }
            } catch (CommandSyntaxException e) {
                throw new HttpException("Missing or malformed block ID: " + blockString + " (" + e.getMessage() + ")", 400);
            }
        });

        int[][] heightmap = initHeightmapData();

        getChunkPosList().parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            CustomHeightmap customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, blockStateList, blockTagKeyList, yMin, yMax);
            getFirstAvailableHeightAt(heightmap, chunkPos, null, customChunkHeightmap);
        });

        return heightmap;
    }

    private static int[][] getHeightmap(ServerLevel serverlevel, Optional<Integer> yMin, Optional<Integer> yMax, String heightmapTypeString) {
        int[][] heightmap = initHeightmapData();

        // Check if the type is a valid heightmap type
        Heightmap.Types defaultHeightmapType = Enums.getIfPresent(Heightmap.Types.class, heightmapTypeString).orNull();
        CustomHeightmap.Types customHeightmapType = Enums.getIfPresent(CustomHeightmap.Types.class, heightmapTypeString).orNull();
        if (defaultHeightmapType == null && customHeightmapType == null) {
            throw new HttpException("heightmap type " + heightmapTypeString + " is not supported.", 400);
        }

        getChunkPosList().parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            // Get the heightmap of type
            Heightmap defaultChunkHeightmap = null;
            CustomHeightmap customChunkHeightmap = null;
            if (defaultHeightmapType != null) {
                defaultChunkHeightmap = chunk.getOrCreateHeightmapUnprimed(defaultHeightmapType);
            } else {
                customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, customHeightmapType, yMin, yMax);
            }
            getFirstAvailableHeightAt(heightmap, chunkPos, defaultChunkHeightmap, customChunkHeightmap);
        });

        // Return the completed heightmap array
        return heightmap;
    }

    private static int[][] initHeightmapData() {
        // Get the x/z size of the build area
        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();
        int xSize = buildArea.box.getXSpan();
        int zSize = buildArea.box.getZSpan();
        // Create the 2D array to store the heightmap data
        return new int[xSize][zSize];
    }

    private static String formatBlockTagKeyLocation(String inputBlockTagKey) {
        if (inputBlockTagKey.startsWith("#")) {
            inputBlockTagKey = inputBlockTagKey.replaceFirst("^#", "");
        }
        if (!inputBlockTagKey.contains(":")) {
            return "minecraft:" + inputBlockTagKey;
        }
        return inputBlockTagKey;
    }

    private static boolean isExistingBlockTagKey(String blockTagKeyString, CommandSourceStack commandSourceStack) {
        // Since fluid tag keys are not included in any public list (that I know of), check for these manually.
        // https://minecraft.wiki/w/Tag#Fluid_tags
        if (blockTagKeyString.equals("minecraft:water") || blockTagKeyString.equals("minecraft:lava")) {
            return true;
        }
        // Check if block tag key exists https://minecraft.wiki/w/Tag#Block_tags_2
        return BlocksHandler.getBlockRegistryLookup(commandSourceStack).listTags().anyMatch((existingTag) -> {
            #if (MC_VER == MC_1_19_2)
            return existingTag.location().toString().equals(blockTagKeyString);
            #else
	        return existingTag.key().location().toString().equals(blockTagKeyString);
            #endif
        });
    }

    private static ArrayList<ChunkPos> getChunkPosList() {
        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();
        ArrayList<ChunkPos> chunkPosList = new ArrayList<>();
        for (int chunkX = buildArea.sectionFrom.x; chunkX <= buildArea.sectionTo.x; chunkX++) {
            for (int chunkZ = buildArea.sectionFrom.z; chunkZ <= buildArea.sectionTo.z; chunkZ++) {
                chunkPosList.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunkPosList;
    }

    private static void getFirstAvailableHeightAt(int[][] heightmap, ChunkPos chunkPos, Heightmap defaultHeightmap, CustomHeightmap customHeightmap) {
        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();
        // For every combination of x and z in that chunk
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMaxZ = chunkPos.getMaxBlockZ();
        for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                // If the column is out of bounds skip it
                if (buildArea.isOutsideBuildArea(x, z)) {
                    continue;
                }
                // Set the value in the heightmap array
                int height = defaultHeightmap != null ?
                    defaultHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ) :
                    customHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ);
                heightmap[x - buildArea.from.getX()][z - buildArea.from.getZ()] = height;
            }
        }
    }
}

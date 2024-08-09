package nl.nielspoldervaart.gdmc.common.handlers;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
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

        // Get comma-separated list of block IDs or block tag keys to use for custom heightmap definition.
        String customTransparentBlocksString = queryParams.getOrDefault("blocks", "");
        Stream<String> customTransparentBlocksList = customTransparentBlocksString.isBlank() ? null : Arrays.stream(customTransparentBlocksString.split(","));

        // Get heightmap preset type argument. Default to WORLD_SURFACE as the default type.
        String heightmapType = queryParams.getOrDefault("type", "WORLD_SURFACE");

        // The dimension to get the heightmap data from.
        String dimension = queryParams.getOrDefault("dimension", null);

        ServerLevel level = getServerLevel(dimension);

        // Preset heightmap type parameter is ignored if custom block list is not empty.
        int[][] heightmap = customTransparentBlocksList != null ?
            getHeightmap(level, dimension, customTransparentBlocksList) :
            getHeightmap(level, heightmapType);

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }

    private int[][] getHeightmap(ServerLevel serverlevel, String dimension, Stream<String> blockList) {

        CommandSourceStack commandSourceStack = createCommandSource(
            "GDMC-HeightmapHandler",
            dimension,
            BuildArea.getBuildArea().box.getCenter().getCenter()
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
                        BlocksHandler.getBlockRegisteryLookup(commandSourceStack),
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
            CustomHeightmap customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, blockStateList, blockTagKeyList, Integer.MAX_VALUE);
            getFirstAvailableHeightAt(heightmap, chunkPos, null, customChunkHeightmap);
        });

        return heightmap;
    }

    private static int[][] getHeightmap(ServerLevel serverlevel, String heightmapTypeString) {
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
                customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, customHeightmapType);
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

    public static String formatBlockTagKeyLocation(String inputBlockTagKey) {
        if (inputBlockTagKey.startsWith("#")) {
            inputBlockTagKey = inputBlockTagKey.replaceFirst("^#", "");
        }
        if (!inputBlockTagKey.contains(":")) {
            return "minecraft:" + inputBlockTagKey;
        }
        return inputBlockTagKey;
    }

    public static boolean isExistingBlockTagKey(String blockTagKeyString, CommandSourceStack commandSourceStack) {
        return BlocksHandler.getBlockRegisteryLookup(commandSourceStack).listTags().anyMatch((existingTag) -> existingTag.key().location().toString().equals(blockTagKeyString));
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

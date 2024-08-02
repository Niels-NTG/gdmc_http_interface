package nl.nielspoldervaart.gdmc.common.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

public class HeightmapHandler extends HandlerBase {

    // GET/POST: the dimension to get the heightmap data from
    private String dimension;

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        // Get query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // Try to parse a type argument from them
        String heightmapType = queryParams.getOrDefault("type", "WORLD_SURFACE");

        dimension = queryParams.getOrDefault("dimension", null);

        int[][] heightmap;

        switch (httpExchange.getRequestMethod().toLowerCase()) {
            case "get" -> {
                heightmap = getHeightmapHandler(heightmapType);
            }
            case "post" -> {
                heightmap = postHeightmapHandler(httpExchange.getRequestBody());
            }
            default -> throw new HttpException("Method not allowed. Only GET and POST requests are supported.", 405);
        }

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }

    private int[][] getHeightmapHandler(String heightmapType) {
        return getHeightmap(getServerLevel(dimension), heightmapType);
    }

    private int[][] postHeightmapHandler(InputStream requestBody) {
        JsonObject customHeightMap = parseJsonObject(requestBody);

        ServerLevel serverLevel = getServerLevel(dimension);

        JsonArray blockList = new JsonArray();
        if (customHeightMap.has("blocks") && customHeightMap.getAsJsonArray("blocks").isJsonArray()) {
            blockList = customHeightMap.getAsJsonArray("blocks");
        }

        boolean transparentLiquids = false;
        if (customHeightMap.has("transparentLiquids") && customHeightMap.getAsJsonPrimitive("transparentLiquids").isBoolean()) {
            transparentLiquids = customHeightMap.getAsJsonPrimitive("transparentLiquids").getAsBoolean();
        }

        int fromY = Integer.MAX_VALUE;
        if (customHeightMap.has("fromY") && customHeightMap.getAsJsonPrimitive("fromY").isNumber()) {
            try {
                fromY = customHeightMap.getAsJsonPrimitive("fromY").getAsInt();
            } catch (NumberFormatException e) {
                throw new HttpException("Value of fromY is not a valid integer.", 400);
            }
            if (fromY > serverLevel.getMaxBuildHeight() || fromY < serverLevel.getMinBuildHeight()) {
                throw new HttpException("Value of fromY is outside of the boundaries of the level.", 400);
            }
        }

        CommandSourceStack commandSourceStack = createCommandSource(
            "GDMC-HeightmapHandler",
            dimension,
            BuildArea.getBuildArea().box.getCenter().getCenter()
        );

        return getHeightmap(serverLevel, commandSourceStack, blockList, transparentLiquids, fromY);
    }

    private static int[][] initHeightmapData() {
        // Get the x/z size of the build area
        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();
        int xSize = buildArea.box.getXSpan();
        int zSize = buildArea.box.getZSpan();
        // Create the 2D array to store the heightmap data
        return new int[xSize][zSize];
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

    private static int[][] getHeightmap(ServerLevel serverlevel, CommandSourceStack commandSourceStack, JsonArray blockList, boolean transparentLiquids, int fromY) {
        ArrayList<BlockState> blockStateList = new ArrayList<>();
        for (JsonElement jsonElement : blockList.asList()) {
	        try {
                String blockString = jsonElement.getAsString();
	            BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(
	                BlocksHandler.getBlockRegisteryLookup(commandSourceStack),
	                new StringReader(blockString),
	                true
	            );
	            blockStateList.add(blockResult.blockState());
	        } catch (UnsupportedOperationException | IllegalStateException | CommandSyntaxException e) {
                throw new HttpException("Missing or malformed block ID " + jsonElement + " (" + e.getMessage() + ")", 400);
	        }
        }

        int[][] heightmap = initHeightmapData();

        getChunkPosList().parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            CustomHeightmap customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, blockStateList, transparentLiquids, fromY);
            getFirstAvailableHeightAt(heightmap, chunkPos, null, customChunkHeightmap);
        });

        return heightmap;
    }
}

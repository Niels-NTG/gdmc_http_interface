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
import java.util.Map;

public class HeightmapHandler extends HandlerBase {

    public HeightmapHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        String method = httpExchange.getRequestMethod().toLowerCase();

        if (!method.equals("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // Get query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // Try to parse a type argument from them
        String inputHeightmapPreset = queryParams.getOrDefault("type", "WORLD_SURFACE");

        String inputCustomBlockList = queryParams.get("blocks");
        String[] customBlockList = inputCustomBlockList == null ? new String[0] : inputCustomBlockList.split(",");

        String dimension = queryParams.getOrDefault("dimension", null);

        // Get a reference to the map/level
        ServerLevel serverlevel = getServerLevel(dimension);

        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();

        int[][] heightmap;
        if (customBlockList.length > 0) {
            CommandSourceStack commandSourceStack = createCommandSource(
                "GDMC-HeightmapHandler",
                dimension,
                buildArea.box.getCenter().getCenter()
            );
            heightmap = getHeightmap(serverlevel, customBlockList, commandSourceStack);
        } else {
            heightmap = getHeightmap(serverlevel, inputHeightmapPreset);
        }

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
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

    private static int[][] getHeightmap(ServerLevel serverlevel, String[] blockList, CommandSourceStack commandSourceStack) {
        int[][] heightmap = initHeightmapData();

        ArrayList<BlockState> blockStateList = new ArrayList<>();
        for (String blockString : blockList) {
            try {
                BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(
                    BlocksHandler.getBlockRegisteryLookup(commandSourceStack),
                    new StringReader(blockString),
                    true
                );
                blockStateList.add(blockResult.blockState());
            } catch (CommandSyntaxException e) {
                System.out.println(blockString + " is not a valid block. " + e.getMessage());
            }
        }

        getChunkPosList().parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            CustomHeightmap customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, blockStateList);
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
}

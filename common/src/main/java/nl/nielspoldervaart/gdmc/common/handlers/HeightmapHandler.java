package nl.nielspoldervaart.gdmc.common.handlers;

import net.minecraft.world.level.ChunkPos;
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
        String heightmapTypeString = queryParams.getOrDefault("type", "WORLD_SURFACE");
        // Check if the type is a valid heightmap type

        String dimension = queryParams.getOrDefault("dimension", null);

        // Get a reference to the map/level
        ServerLevel serverlevel = getServerLevel(dimension);

        // Get the heightmap of that type
        int[][] heightmap = getHeightmap(serverlevel, heightmapTypeString);

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }

    private static int[][] getHeightmap(ServerLevel serverlevel, String heightmapTypeString) {

        // Get the x/z size of the build area
        BuildArea.BuildAreaInstance buildArea = BuildArea.getBuildArea();
        int xSize = buildArea.box.getXSpan();
        int zSize = buildArea.box.getZSpan();
        // Create the 2D array to store the heightmap data
        int[][] heightmap = new int[xSize][zSize];

        // Check if the type is a valid heightmap type
        Heightmap.Types defaultHeightmapType = Enums.getIfPresent(Heightmap.Types.class, heightmapTypeString).orNull();
        CustomHeightmap.Types customHeightmapType = Enums.getIfPresent(CustomHeightmap.Types.class, heightmapTypeString).orNull();
        if (defaultHeightmapType == null && customHeightmapType == null) {
            throw new HttpException("heightmap type " + heightmapTypeString + " is not supported.", 400);
        }

        ArrayList<ChunkPos> chunkPosList = new ArrayList<>();
        for (int chunkX = buildArea.sectionFrom.x; chunkX <= buildArea.sectionTo.x; chunkX++) {
            for (int chunkZ = buildArea.sectionFrom.z; chunkZ <= buildArea.sectionTo.z; chunkZ++) {
                chunkPosList.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        chunkPosList.parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);

            // Get the heightmap of type
            Heightmap defaultChunkHeightmap = null;
            CustomHeightmap customChunkHeightmap = null;
            if (defaultHeightmapType != null) {
                defaultChunkHeightmap = chunk.getOrCreateHeightmapUnprimed(defaultHeightmapType);
            } else if (customHeightmapType != null) {
                customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, customHeightmapType);
            }

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
                    int height = defaultChunkHeightmap != null ?
                        defaultChunkHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ) :
                        customChunkHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ);
                    heightmap[x - buildArea.from.getX()][z - buildArea.from.getZ()] = height;
                }
            }
        });

        // Return the completed heightmap array
        return heightmap;
    }
}

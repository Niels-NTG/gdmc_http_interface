package nl.nielspoldervaart.gdmc.common.handlers;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.criterion.MinMaxBounds.Ints;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
import java.util.HashSet;
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

        // x, z positions
        int x;
        int z;
        int dx;
        int dz;

        // If true, constrain placement/getting blocks within the current build area.
        boolean withinBuildArea;

        BuildArea.BuildAreaInstance buildArea = null;
        try {
            buildArea = BuildArea.getBuildArea();
        } catch (HttpException ignored) {}

        try {
            if (queryParams.get("x") == null && buildArea != null) {
                x = buildArea.from.getX();
            } else {
                x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
            }

            if (queryParams.get("z") == null && buildArea != null) {
                z = buildArea.from.getZ();
            } else {
                z = Integer.parseInt(queryParams.getOrDefault("z", "0"));
            }

            if (queryParams.get("dx") == null && buildArea != null) {
                dx = buildArea.box.getXSpan();
            } else {
                dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
            }

            if (queryParams.get("dz") == null && buildArea != null) {
                dz = buildArea.box.getZSpan();
            } else {
                dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));
            }

            withinBuildArea = Boolean.parseBoolean(queryParams.getOrDefault("withinBuildArea", "false"));
        } catch (NumberFormatException e) {
            String message = "Could not parse query parameter: " + e.getMessage();
            throw new HttpException(message, 400);
        }

        BoundingBox box = BuildArea.clampChunksToBuildArea(createBoundingBox(
            x, 0, z,
            dx, 0, dz
        ), withinBuildArea);

        // Get comma-separated list of block IDs, block tag keys or fluid tag keys to use for custom heightmap definition.
        // https://minecraft.wiki/w/Java_Edition_data_values#Blocks
        // https://minecraft.wiki/w/Fluid_tag_(Java_Edition)
        String customTransparentBlocksString = queryParams.getOrDefault("blocks", "");
        Stream<String> customTransparentBlocksList = customTransparentBlocksString.isBlank() ?
            null :
            Arrays.stream(customTransparentBlocksString.split(","));

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
				Ints yBounds = RangeArgument.intRange().parse(new StringReader(yBoundsInput));
                yMin = yBounds.min();
                yMax = yBounds.max();
                if (yMin.isPresent() && yMax.isPresent() && yMin.get() - yMax.get() == 0) {
                    throw new HttpException("yBounds should span more than 0", 400);
                }
            } catch (CommandSyntaxException e) {
                throw new HttpException("yBounds formatted incorrectly: " + e.getMessage(), 400);
	        }
        }

        // Preset heightmap type parameter is ignored if custom block list is not empty.
        int[][] heightmap = customTransparentBlocksList != null ?
            getHeightmap(level, box, yMin, yMax, customTransparentBlocksList) :
            getHeightmap(level, box, yMin, yMax, heightmapType);

        // Respond with that array as a string
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);
        resolveRequest(httpExchange, new Gson().toJson(heightmap));
    }

    private int[][] getHeightmap(ServerLevel serverlevel, BoundingBox box, Optional<Integer> yMin, Optional<Integer> yMax, Stream<String> blockList) {
        CommandSourceStack commandSourceStack = createCommandSource(
            "GDMC-HeightmapHandler",
            serverlevel
        );

        ArrayList<BlockStateParser.BlockResult> blockStateParserList = new ArrayList<>();
        ArrayList<String> blockTagKeyList = new ArrayList<>();
        blockList.forEach(blockString -> {
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
                    blockStateParserList.add(blockResult);
                }
            } catch (CommandSyntaxException e) {
                throw new HttpException("Missing or malformed block ID: " + blockString + " (" + e.getMessage() + ")", 400);
            }
        });

        int[][] heightmap = initHeightmapData(box);

        getChunkPosList(box).parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            CustomHeightmap customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, blockStateParserList, blockTagKeyList, yMin, yMax);
            getFirstAvailableHeightAt(heightmap, box, chunkPos, null, customChunkHeightmap);
        });

        return heightmap;
    }

    private static int[][] getHeightmap(ServerLevel serverlevel, BoundingBox box, Optional<Integer> yMin, Optional<Integer> yMax, String heightmapTypeString) {
        int[][] heightmap = initHeightmapData(box);

        // Check if the type is a valid heightmap type
        Heightmap.Types defaultHeightmapType = Enums.getIfPresent(Heightmap.Types.class, heightmapTypeString).orNull();
        CustomHeightmap.Types customHeightmapType = Enums.getIfPresent(CustomHeightmap.Types.class, heightmapTypeString).orNull();
        if (defaultHeightmapType == null && customHeightmapType == null) {
            throw new HttpException("heightmap type " + heightmapTypeString + " is not supported.", 400);
        }

        getChunkPosList(box).parallelStream().forEach(chunkPos -> {
            LevelChunk chunk = serverlevel.getChunk(chunkPos.x, chunkPos.z);
            // Get the heightmap of type
            Heightmap defaultChunkHeightmap = null;
            CustomHeightmap customChunkHeightmap = null;
            if (defaultHeightmapType != null) {
                defaultChunkHeightmap = chunk.getOrCreateHeightmapUnprimed(defaultHeightmapType);
            } else {
                customChunkHeightmap = CustomHeightmap.primeHeightmaps(chunk, customHeightmapType, yMin, yMax);
            }
            getFirstAvailableHeightAt(heightmap, box, chunkPos, defaultChunkHeightmap, customChunkHeightmap);
        });

        // Return the completed heightmap array
        return heightmap;
    }

    private static int[][] initHeightmapData(BoundingBox box) {
        // Create the 2D array to store the heightmap data
        return new int[box.getXSpan()][box.getZSpan()];
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
        return BlocksHandler.getBlockRegistryLookup(commandSourceStack).listTags().anyMatch((existingTag) -> existingTag.key().location().toString().equals(blockTagKeyString));
    }

    private static HashSet<ChunkPos> getChunkPosList(BoundingBox box) {
        HashSet<ChunkPos> chunkPosSet = new HashSet<>();
        for (int rangeX = box.minX(); rangeX <= box.maxX(); rangeX++) {
            for (int rangeZ = box.minZ(); rangeZ <= box.maxZ(); rangeZ++) {
                chunkPosSet.add(new ChunkPos(
                    SectionPos.blockToSectionCoord(rangeX),
                    SectionPos.blockToSectionCoord(rangeZ)
                ));
            }
        }
        return chunkPosSet;
    }

    private static void getFirstAvailableHeightAt(int[][] heightmap, BoundingBox box, ChunkPos chunkPos, Heightmap defaultHeightmap, CustomHeightmap customHeightmap) {
        // For every combination of x and z in that chunk
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMaxZ = chunkPos.getMaxBlockZ();
        for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                // If the column is out of bounds, skip it.
                if (!(x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ())) {
                    continue;
                }
                // Set the value in the heightmap array
                int height = defaultHeightmap != null ?
                    defaultHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ) :
                    customHeightmap.getFirstAvailable(x - chunkMinX, z - chunkMinZ);
                heightmap[x - box.minX()][z - box.minZ()] = height;
            }
        }
    }
}

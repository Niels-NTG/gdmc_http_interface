package org.ntg.gdmc.gdmchttpinterface.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.ntg.gdmc.gdmchttpinterface.utils.BuildArea;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class BiomesHandler extends HandlerBase {

	public BiomesHandler(MinecraftServer mcServer) {
		super(mcServer);
	}

	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {
		// query parameters
		Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

		// GET: x, y, z positions
		int x;
		int y;
		int z;

		// GET: Ranges in the x, y, z directions (can be negative). Defaults to 1.
		int dx;
		int dy;
		int dz;

		// GET: if true, constrain getting biomes within the current build area.
		boolean withinBuildArea;

		String dimension;

		try {
			x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
			y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
			z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

			dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
			dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
			dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

			withinBuildArea = Boolean.parseBoolean(queryParams.getOrDefault("withinBuildArea", "false"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			String message = "Could not parse query parameter: " + e.getMessage();
			throw new HandlerBase.HttpException(message, 400);
		}

		String method = httpExchange.getRequestMethod().toLowerCase();

		if (!method.equals("get")) {
			throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		// Response headers
		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		JsonArray responseList = new JsonArray();

		ServerLevel serverLevel = getServerLevel(dimension);

		// Calculate boundaries of area of blocks to gather biome information on.
		BoundingBox box;
		try {
			box = BuildArea.clampToBuildArea(createBoundingBox(x, y, z, dx, dy, dz), withinBuildArea);
		} catch (HttpException e) {
			resolveRequest(httpExchange, responseList.toString());
			return;
		}

		// Create an ordered map with an entry for every block position we want to know the biome of.
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
		// Gather biome information for each position in parallel.
		blockPosMap.keySet().parallelStream().forEach(blockPos -> {
			LevelChunk levelChunk = chunkPosMap.get(new ChunkPos(blockPos));
			Optional<ResourceKey<Biome>> biomeResourceKey = levelChunk.getNoiseBiome(blockPos.getX(), blockPos.getY(), blockPos.getZ()).unwrapKey();
			if (biomeResourceKey.isPresent()) {
				String biomeName = "";
				if (!serverLevel.isOutsideBuildHeight(blockPos)) {
					biomeName = biomeResourceKey.get().location().toString();
				}
				JsonObject json = new JsonObject();
				json.addProperty("id", biomeName);
				json.addProperty("x", blockPos.getX());
				json.addProperty("y", blockPos.getY());
				json.addProperty("z", blockPos.getZ());
				blockPosMap.replace(blockPos, json);
			}
		});
		// Create a JsonArray with JsonObject, each contain a key-value pair for
		// the x, y, z position and the namespaced biome name.
		for (JsonObject biomeJson : blockPosMap.values()) {
			responseList.add(biomeJson);
		}

		resolveRequest(httpExchange, responseList.toString());
	}
}

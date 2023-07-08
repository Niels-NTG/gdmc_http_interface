package org.ntg.gdmc.gdmchttpinterface.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
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

		// GET is true, constrain getting biomes within the current build area.
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

		JsonArray responseList = new JsonArray();

		if (method.equals("get")) {
			ServerLevel serverLevel = getServerLevel(dimension);

			// Calculate boundaries of area of blocks to gather biome information on.
			int xOffset = x + dx;
			int xMin = Math.min(x, xOffset);
			int xMax = Math.max(x, xOffset);

			int yOffset = y + dy;
			int yMin = Math.min(y, yOffset);
			int yMax = Math.max(y, yOffset);

			int zOffset = z + dz;
			int zMin = Math.min(z, zOffset);
			int zMax = Math.max(z, zOffset);

			BuildAreaHandler.BuildArea buildArea = null;
			if (withinBuildArea) {
				buildArea = BuildAreaHandler.getBuildArea();
				if (buildArea == null) {
					throw new HttpException("No build area is specified. Use the setbuildarea command inside Minecraft to set a build area.", 404);
				}
			}

			// Create a JsonArray with JsonObject, each contain a key-value pair for
			// the x, y, z position and the namespaced biome name.
			for (int rangeX = xMin; rangeX < xMax; rangeX++) {
				for (int rangeY = yMin; rangeY < yMax; rangeY++) {
					for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
						BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
						if (withinBuildArea && buildArea != null && buildArea.isOutsideBuildArea(blockPos)) {
							continue;
						}
						Optional<ResourceKey<Biome>> biomeResourceKey = serverLevel.getBiome(blockPos).unwrapKey();
						if (biomeResourceKey.isEmpty()) {
							continue;
						}
						String biomeName = "";
						if (!serverLevel.isOutsideBuildHeight(blockPos)) {
							biomeName = biomeResourceKey.get().location().toString();
						}
						JsonObject json = new JsonObject();
						json.addProperty("id", biomeName);
						json.addProperty("x", rangeX);
						json.addProperty("y", rangeY);
						json.addProperty("z", rangeZ);
						responseList.add(json);
					}
				}
			}
		} else {
			throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		// Response headers
		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		resolveRequest(httpExchange, responseList.toString());
	}
}

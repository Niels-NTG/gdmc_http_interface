package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

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

		String dimension;

		try {
			x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
			y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
			z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

			dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
			dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
			dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			String message = "Could not parse query parameter: " + e.getMessage();
			throw new HandlerBase.HttpException(message, 400);
		}

		// Check if clients wants a response in a JSON format. If not, return response in plain text.
		Headers requestHeaders = httpExchange.getRequestHeaders();
		String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
		boolean returnJson = hasJsonTypeInHeader(acceptHeader);

		String method = httpExchange.getRequestMethod().toLowerCase();

		String responseString;

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

			if (returnJson) {
				// Create a JsonArray with JsonObject, each contain a key-value pair for
				// the x, y, z position and the namespaced biome name.
				JsonArray jsonArray = new JsonArray();
				for (int rangeX = xMin; rangeX < xMax; rangeX++) {
					for (int rangeY = yMin; rangeY < yMax; rangeY++) {
						for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
							BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
							if (serverLevel.getBiome(blockPos).unwrapKey().isEmpty()) {
								continue;
							}
							String biomeName = serverLevel.getBiome(blockPos).unwrapKey().get().location().toString();
							JsonObject json = new JsonObject();
							json.addProperty("id", biomeName);
							json.addProperty("x", rangeX);
							json.addProperty("y", rangeY);
							json.addProperty("z", rangeZ);
							jsonArray.add(json);
						}
					}
				}
				responseString = new Gson().toJson(jsonArray);
			} else {
				// Create list of \n-separated strings containing the space-separated
				// x, y, z position and the namespaced biome name.
				ArrayList<String> biomesList = new ArrayList<>();
				for (int rangeX = xMin; rangeX < xMax; rangeX++) {
					for (int rangeY = yMin; rangeY < yMax; rangeY++) {
						for (int rangeZ = zMin; rangeZ < zMax; rangeZ++) {
							BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
							if (serverLevel.getBiome(blockPos).unwrapKey().isEmpty()) {
								continue;
							}
							String biomeName = serverLevel.getBiome(blockPos).unwrapKey().get().location().toString();
							biomesList.add(rangeX + " " + rangeY + " " + rangeZ + " " + biomeName);
						}
					}
				}
				responseString = String.join("\n", biomesList);
			}
		} else {
			throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		// Response headers
		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);
		if (returnJson) {
			setResponseHeadersContentTypeJson(responseHeaders);
		} else {
			setResponseHeadersContentTypePlain(responseHeaders);
		}

		resolveRequest(httpExchange, responseString);
	}
}

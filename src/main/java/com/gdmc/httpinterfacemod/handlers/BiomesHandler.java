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
		int x;
		int y;
		int z;
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

		Headers reqestHeaders = httpExchange.getRequestHeaders();
		String contentType = getHeader(reqestHeaders, "Accept", "*/*");
		boolean returnJson = contentType.equals("application/json") || contentType.equals("text/json");

		String method = httpExchange.getRequestMethod().toLowerCase();
		String responseString;

		if (method.equals("get")) {
			ServerLevel serverLevel = getServerLevel(dimension);
			if (returnJson) {
				JsonArray jsonArray = new JsonArray();
				for (int rangeX = x; rangeX < x + dx; rangeX++) {
					for (int rangeY = y; rangeY < y + dy; rangeY++) {
						for (int rangeZ = z; rangeZ < z + dz; rangeZ++) {
							BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
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
				ArrayList<String> biomesList = new ArrayList<>();
				for (int rangeX = x; rangeX < x + dx; rangeX++) {
					for (int rangeY = y; rangeY < y + dy; rangeY++) {
						for (int rangeZ = z; rangeZ < z + dz; rangeZ++) {
							BlockPos blockPos = new BlockPos(rangeX, rangeY, rangeZ);
							String biomeName = serverLevel.getBiome(blockPos).unwrapKey().get().location().toString();
							biomesList.add(rangeX + " " + rangeY + " " + rangeZ + " " + biomeName);
						}
					}
				}
				responseString = String.join("\n", biomesList);
			}
		} else {
			throw new HandlerBase.HttpException("Method not allowed. Only GET requests are supported.", 405);
		}

		Headers headers = httpExchange.getResponseHeaders();
		addDefaultHeaders(headers);

		if(returnJson) {
			headers.add("Content-Type", "application/json; charset=UTF-8");
		} else {
			headers.add("Content-Type", "text/plain; charset=UTF-8");
		}

		resolveRequest(httpExchange, responseString);
	}
}

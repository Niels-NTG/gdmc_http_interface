package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EntitiesHandler extends HandlerBase {

	private String dimension;

	public EntitiesHandler(MinecraftServer mcServer) {
		super(mcServer);
	}
	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {

		// query parameters
		Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

		// PUT/GET: x, y, z positions
		int x;
		int y;
		int z;

		// GET: Ranges in the x, y, z directions (can be negative). Defaults to 1.
		int dx;
		int dy;
		int dz;

		// GET: Whether to include entity data https://minecraft.fandom.com/wiki/Entity_format#Entity_Format
		boolean includeData;

//		TODO add includeData parameter

		try {
			x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
			y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
			z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

			dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
			dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
			dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

			includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			String message = "Could not parse query parameter: " + e.getMessage();
			throw new HttpException(message, 400);
		}

		// Check if clients wants a response in a JSON format. If not, return response in plain text.
		Headers requestHeaders = httpExchange.getRequestHeaders();
		String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
		boolean returnJson = hasJsonTypeInHeader(acceptHeader);

		String method = httpExchange.getRequestMethod().toLowerCase();

		String responseString;

		switch (method) {
			case "put" -> {
				String contentTypeHeader = getHeader(requestHeaders, "Content-Type", "*/*");
				boolean parseRequestAsJson = hasJsonTypeInHeader(contentTypeHeader);
				responseString = putEntitiesHandler(httpExchange.getRequestBody(), parseRequestAsJson, x, y, z, returnJson);
			}
			case "get" -> {
				responseString = getEntitiesHandler(x, y, z, dx, dy, dz, includeData, returnJson);
			}
			case "delete" -> {
				//		TODO DELETE /entities to clear out entities in an area. Look if there are any commands that already do this.
				String contentTypeHeader = getHeader(requestHeaders, "Content-Type", "*/*");
				boolean parseRequestAsJson = hasJsonTypeInHeader(contentTypeHeader);
				responseString = deleteEntitiesHandler(httpExchange.getRequestBody(), parseRequestAsJson, returnJson);
			}
			default -> {
				throw new HttpException("Method not allowed. Only PUT and GET requests are supported.", 405);
			}
		}


		// Response headers
		Headers responseHeaders = httpExchange.getResponseHeaders();
		addDefaultResponseHeaders(responseHeaders);
		if (returnJson) {
			addResponseHeadersContentTypeJson(responseHeaders);
		} else {
			addResponseHeadersContentTypePlain(responseHeaders);
		}

		resolveRequest(httpExchange, responseString);

	}

	private String putEntitiesHandler(InputStream requestBody, boolean parseRequestAsJson, int x, int y, int z, boolean returnJson) {
		CommandSourceStack cmdSrc = createCommandSource("GDMC-EntitiesHandler", dimension).withPosition(new Vec3(x, y, z));

		ArrayList<String> summonCommands = new ArrayList<>();
		if (parseRequestAsJson) {
			JsonArray entityDescriptionList;
			try {
				entityDescriptionList = JsonParser.parseReader(new InputStreamReader(requestBody)).getAsJsonArray();
			} catch (JsonSyntaxException jsonSyntaxException) {
				throw new HttpException("Malformed JSON: " + jsonSyntaxException.getMessage(), 400);
			}

			for (JsonElement entityDescription : entityDescriptionList) {
				JsonObject json = entityDescription.getAsJsonObject();
				String entityName = json.has("id") ? json.get("id").getAsString() : "";
				Vec3 referencePosition = cmdSrc.getPosition();
				String posXString = json.has("x") ? json.get("x").getAsString() : String.valueOf(referencePosition.x);
				String posYString = json.has("y") ? json.get("y").getAsString() : String.valueOf(referencePosition.y);
				String posZString = json.has("z") ? json.get("z").getAsString() : String.valueOf(referencePosition.z);
				String entityData = json.has("data") ? json.get("data").toString() : "";
				summonCommands.add("%s %s %s %s %s".formatted(entityName, posXString, posYString, posZString, entityData));
			}
		} else {
			summonCommands.addAll(new BufferedReader(new InputStreamReader(requestBody)).lines().toList());
		}

		ArrayList<String> returnValues = new ArrayList<>();
		for (String summonCommand : summonCommands) {
			if (summonCommand.length() == 0) {
				continue;
			}
			CompletableFuture<String> cfs = CompletableFuture.supplyAsync(() -> {
				String str;
				try {
					str = "" + mcServer.getCommands().getDispatcher().execute(
						"summon " + summonCommand,
						cmdSrc
					);
				} catch (CommandSyntaxException e) {
					str = e.getMessage();
				}
				return str;
			}, mcServer);
			String results = cfs.join();
			returnValues.add(results);
		}

		// Set response as a list of "1" (entity was placed), "0" (entity was not placed) or an exception string if something went wrong.
		if (returnJson) {
			return new Gson().toJson(returnValues);
		}
		return String.join("\n", returnValues);
	}

	private String getEntitiesHandler(int x, int y, int z, int dx, int dy, int dz, boolean includeData, boolean returnJson) {

		// Calculate boundaries of area of blocks to gather information on.
		int xOffset = x + dx;
		int xMin = Math.min(x, xOffset);
		int xMax = Math.max(x, xOffset);

		int yOffset = y + dy;
		int yMin = Math.min(y, yOffset);
		int yMax = Math.max(y, yOffset);

		int zOffset = z + dz;
		int zMin = Math.min(z, zOffset);
		int zMax = Math.max(z, zOffset);

		ServerLevel level = getServerLevel(dimension);

		List<Entity> entityList = level.getEntities(null, new AABB(xMin, yMin, zMin, xMax, yMax, zMax));

		if (returnJson) {
			// Create a JsonArray with JsonObject, each contain a key-value pair for
			// the x, y, z position, the block ID, the block state (if requested and available)
			// and the block entity data (if requested and available).
			JsonArray jsonArray = new JsonArray();
			for (Entity entity : entityList) {
				String entityId = entity.getEncodeId();
				if (entityId == null) {
					continue;
				}
				JsonObject json = new JsonObject();
				json.addProperty("id", entityId);
				json.addProperty("x", entity.getX());
				json.addProperty("y", entity.getY());
				json.addProperty("z", entity.getZ());
				json.addProperty("uuid", entity.getStringUUID());
				if (includeData) {
					json.add("data", getEntityDataAsJsonObject(entity));
				}
				jsonArray.add(json);
			}
			return new Gson().toJson(jsonArray);
		}

		ArrayList<String> responseList = new ArrayList<>();
		for (Entity entity : entityList) {
			String entityId = entity.getEncodeId();
			if (entityId == null) {
				continue;
			}
			responseList.add(
				"%s %s %s %s %s%s".formatted(
					entityId,
					entity.getX(), entity.getY(), entity.getZ(),
					includeData ? getEntityDataAsStr(entity) : "",
					entity.getStringUUID()
				)
			);
		}
		return String.join("\n", responseList);

	}

	private String deleteEntitiesHandler(InputStream requestBody, boolean parseRequestAsJson, boolean returnJson) {

		ServerLevel level = getServerLevel(dimension);

		List<String> entityUUIDToBeRemoved;

		List<String> returnValues = new ArrayList<>();

		if (parseRequestAsJson) {
			JsonArray jsonListUUID;
			try {
				jsonListUUID = JsonParser.parseReader(new InputStreamReader(requestBody)).getAsJsonArray();
			} catch (JsonSyntaxException jsonSyntaxException) {
				throw new HttpException("Malformed JSON: " + jsonSyntaxException.getMessage(), 400);
			}
			entityUUIDToBeRemoved = Arrays.asList(new Gson().fromJson(jsonListUUID, String[].class));
		} else {
			entityUUIDToBeRemoved = new BufferedReader(new InputStreamReader(requestBody)).lines().toList();
		}

		for (String stringUUID : entityUUIDToBeRemoved) {
			if (stringUUID.length() == 0) {
				continue;
			}
			Entity entityToBeRemoved;
			try {
				entityToBeRemoved = level.getEntity(UUID.fromString(stringUUID));
			} catch (IllegalArgumentException e) {
				returnValues.add("0");
				continue;
			}

			if (entityToBeRemoved != null) {
				if (entityToBeRemoved.isRemoved()) {
					returnValues.add("0");
				} else {
					entityToBeRemoved.remove(Entity.RemovalReason.DISCARDED);
					returnValues.add("1");
				}
				continue;
			}
			returnValues.add("0");
		}

		if (returnJson) {
			return new Gson().toJson(returnValues);
		}
		return String.join("\n", returnValues);
	}

	private JsonObject getEntityDataAsJsonObject(Entity entity) {
		JsonObject json = new JsonObject();
		CompoundTag tags = entity.serializeNBT();
		if (tags != null) {
			String tagAsJsonString = (new JsonTagVisitor()).visit(tags);
			JsonObject tagsAsJsonObject = JsonParser.parseString(tagAsJsonString).getAsJsonObject();
			if (tagsAsJsonObject != null) {
				return tagsAsJsonObject;
			}
		}
		return json;
	}

	private String getEntityDataAsStr(Entity entity) {
		String str = "{} ";
		CompoundTag tags = entity.serializeNBT();
		if (tags != null) {
			str = tags.getAsString();
		}
		return str;
	}
}

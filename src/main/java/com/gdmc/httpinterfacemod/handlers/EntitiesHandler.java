package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntitySummonArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class EntitiesHandler extends HandlerBase {

	// PUT/GET: x, y, z positions
	private int x;
	private int y;
	private int z;

	// GET: Ranges in the x, y, z directions (can be negative). Defaults to 1.
	private int dx;
	private int dy;
	private int dz;

	// GET: Whether to include entity data https://minecraft.fandom.com/wiki/Entity_format#Entity_Format
	private boolean includeData;
	private String dimension;

	public EntitiesHandler(MinecraftServer mcServer) {
		super(mcServer);
	}
	@Override
	protected void internalHandle(HttpExchange httpExchange) throws IOException {

		// query parameters
		Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

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

		String contentTypeHeader = getHeader(requestHeaders, "Content-Type", "*/*");
		boolean parseRequestAsJson = hasJsonTypeInHeader(contentTypeHeader);

		String responseString;

		switch (method) {
			case "put" -> {
				responseString = putEntitiesHandler(httpExchange.getRequestBody(), parseRequestAsJson, returnJson);
			}
			case "get" -> {
				responseString = getEntitiesHandler(returnJson);
			}
			case "delete" -> {
				responseString = deleteEntitiesHandler(httpExchange.getRequestBody(), parseRequestAsJson, returnJson);
			}
			case "patch" -> {
				responseString = patchEntitiesHandler(httpExchange.getRequestBody(), parseRequestAsJson, returnJson);
			}
			default -> {
				throw new HttpException("Method not allowed. Only PUT, GET, DELETE and PATCH requests are supported.", 405);
			}
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

	/**
	 * @param requestBody request body of entity summon instructions
	 * @param parseRequestAsJson if true, treat input as JSON
	 * @param returnJson if true, return result in JSON format
	 * @return summon results
	 */
	private String putEntitiesHandler(InputStream requestBody, boolean parseRequestAsJson, boolean returnJson) {
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
				// TODO extract position from "Pos"
				String posXString = json.has("x") ? json.get("x").getAsString() : String.valueOf(referencePosition.x);
				String posYString = json.has("y") ? json.get("y").getAsString() : String.valueOf(referencePosition.y);
				String posZString = json.has("z") ? json.get("z").getAsString() : String.valueOf(referencePosition.z);
				String entityData = json.has("data") ? json.get("data").toString() : "";
				summonCommands.add("%s %s %s %s %s".formatted(entityName, posXString, posYString, posZString, entityData));
			}
		} else {
			summonCommands.addAll(new BufferedReader(new InputStreamReader(requestBody)).lines().toList());
		}

		ServerLevel serverLevel = getServerLevel(dimension);

		ArrayList<String> returnValues = new ArrayList<>();
		for (String summonCommand : summonCommands) {
			if (summonCommand.length() == 0) {
				continue;
			}

			StringReader sr = new StringReader(summonCommand);
			ResourceLocation entityResource;
			try {
				entityResource = EntitySummonArgument.id().parse(sr);
				sr.skip();
			} catch (CommandSyntaxException e) {
				returnValues.add("EntitySummonArgument: " + e.getMessage());
				continue;
			}
			Coordinates position;
			try {
				position = Vec3Argument.vec3().parse(sr);
				sr.skip();
			} catch (CommandSyntaxException e) {
				returnValues.add("Vec3Argument: " + e.getMessage());
				continue;
			}

			CompoundTag compoundTag;
			try {
				compoundTag = TagParser.parseTag(sr.getRemaining());
			} catch (CommandSyntaxException | StringIndexOutOfBoundsException e) {
				compoundTag = new CompoundTag();
			}

			if (!Level.isInSpawnableBounds(position.getBlockPos(cmdSrc))) {
				returnValues.add("Invalid position");
				continue;
			}

			compoundTag.putString("id", entityResource.toString());

			Entity entity = EntityType.loadEntityRecursive(compoundTag, serverLevel, (_entity) -> {
				Vec3 positionVector = position.getPosition(cmdSrc);
				_entity.moveTo(positionVector.x, positionVector.y, positionVector.z, _entity.getYRot(), _entity.getXRot());
				return _entity;
			});
			if (entity == null || entity.isRemoved()) {
				returnValues.add("Cannot be spawned");
				continue;
			}

			if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
				returnValues.add("UUID already exists");
				continue;
			}
			returnValues.add(entity.getStringUUID());
		}

		// Set response as a list of "1" (entity was placed), "0" (entity was not placed) or an exception string if something went wrong.
		if (returnJson) {
			return new Gson().toJson(returnValues);
		}
		return String.join("\n", returnValues);
	}

	/**
	 * @param returnJson if true, return resposne in JSON formatted string
	 * @return list of entity information
	 */
	private String getEntitiesHandler(boolean returnJson) {

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
				"%s %s %s %s %s %s".formatted(
					entity.getStringUUID(),
					entityId,
					entity.getX(), entity.getY(), entity.getZ(),
					getEntityDataAsStr(entity)
				)
			);
		}
		return String.join("\n", responseList);
	}

	/**
	 * @param requestBody request body of entity removal instructions
	 * @param parseRequestAsJson if true, treat input as JSON
	 * @param returnJson if true, return result in JSON format
	 * @return entity removal results
	 */
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

	/**
	 * @param requestBody request body of entity patch instructions
	 * @param parseRequestAsJson if true, treat input as JSON
	 * @param returnJson if true, return result in JSON-formatted string
	 * @return entity patch status results
	 */
	private String patchEntitiesHandler(InputStream requestBody, boolean parseRequestAsJson, boolean returnJson) {

		ServerLevel level = getServerLevel(dimension);

		List<String> returnValues = new ArrayList<>();

		if (parseRequestAsJson) {
			JsonArray jsonList;
			try {
				jsonList = JsonParser.parseReader(new InputStreamReader(requestBody)).getAsJsonArray();
			} catch (JsonSyntaxException jsonSyntaxException) {
				throw new HttpException("Malformed JSON: " + jsonSyntaxException.getMessage(), 400);
			}
			for (JsonElement entityDescription : jsonList) {
				JsonObject json = entityDescription.getAsJsonObject();
				PatchEntityInstruction patchEntityInstruction;
				try {
					patchEntityInstruction = new PatchEntityInstruction(json);
				} catch (IllegalArgumentException | CommandSyntaxException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				try {
					if (patchEntityInstruction.applyPatch(level)) {
						returnValues.add("1");
						continue;
					}
				} catch (ReportedException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				returnValues.add("0");
			}
		} else {
			List<String> textList = new BufferedReader(new InputStreamReader(requestBody)).lines().toList();
			for (String entityDescription : textList) {
				PatchEntityInstruction patchEntityInstruction;
				try {
					patchEntityInstruction = new PatchEntityInstruction(entityDescription);
				} catch (IllegalArgumentException | CommandSyntaxException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				try {
					if (patchEntityInstruction.applyPatch(level)) {
						returnValues.add("1");
						continue;
					}
				} catch (ReportedException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				returnValues.add("0");
			}
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
		if (!includeData) {
			return "";
		}
		CompoundTag tags = entity.serializeNBT();
		if (tags != null) {
			return tags.getAsString();
		}
		return "{}";
	}

	/**
	 * Model to encapsulate parsing of data patches, finding existing entities with that {@link UUID}, applying the patch and returning a success/fail status.
	 */
	private final static class PatchEntityInstruction {
		private final UUID uuid;
		private final CompoundTag patchData;

		PatchEntityInstruction(JsonObject inputData) throws IllegalArgumentException, CommandSyntaxException {
			uuid = UUID.fromString(inputData.get("uuid").getAsString());
			patchData = TagParser.parseTag(inputData.get("data").toString());
		}

		PatchEntityInstruction(String inputData) throws IllegalArgumentException, CommandSyntaxException {
			StringReader sr = new StringReader(inputData);
			uuid = UUID.fromString(sr.readStringUntil(' '));
			patchData = TagParser.parseTag(sr.getRemaining());
		}

		public boolean applyPatch(ServerLevel level) throws ReportedException {
			if (uuid == null) {
				return false;
			}
			Entity entity = level.getEntity(uuid);
			if (entity == null) {
				return false;
			}
			CompoundTag patchedData = entity.serializeNBT().merge(patchData);
			if (entity.serializeNBT().equals(patchedData)) {
				return false;
			}
			entity.load(patchedData);
			return true;
		}
	}
}

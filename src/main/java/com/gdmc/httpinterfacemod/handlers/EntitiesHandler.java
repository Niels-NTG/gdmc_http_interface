package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.TagMerger;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntitySummonArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
		ServerLevel serverLevel = getServerLevel(dimension);

		ArrayList<String> returnValues = new ArrayList<>();
		if (parseRequestAsJson) {
			JsonArray entityDescriptionList;
			try {
				entityDescriptionList = JsonParser.parseReader(new InputStreamReader(requestBody)).getAsJsonArray();
			} catch (JsonSyntaxException jsonSyntaxException) {
				throw new HttpException("Malformed JSON: " + jsonSyntaxException.getMessage(), 400);
			}

			for (JsonElement entityDescription : entityDescriptionList) {
				JsonObject json = entityDescription.getAsJsonObject();

				SummonEntityInstruction summonEntityInstruction;
				try {
					summonEntityInstruction = new SummonEntityInstruction(json, cmdSrc);
				} catch (CommandSyntaxException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				returnValues.add(summonEntityInstruction.summon(serverLevel));
			}
		} else {
			List<String> inputList = new BufferedReader(new InputStreamReader(requestBody)).lines().toList();
			for (String inputSummonInstruction : inputList) {
				SummonEntityInstruction summonEntityInstruction;
				try {
					summonEntityInstruction = new SummonEntityInstruction(inputSummonInstruction, cmdSrc);
				} catch (CommandSyntaxException e) {
					returnValues.add(e.getMessage());
					continue;
				}
				returnValues.add(summonEntityInstruction.summon(serverLevel));
			}
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
				json.addProperty("uuid", entity.getStringUUID());
				if (includeData) {
					json.addProperty("data", getEntityDataAsStr(entity));
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
			responseList.add(entity.getStringUUID() + " " + getEntityDataAsStr(entity));
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
				} catch (IllegalArgumentException | CommandSyntaxException | UnsupportedOperationException e) {
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

	private final static class SummonEntityInstruction {

		private ResourceLocation entityResourceLocation;
		private Vec3 entityPosition;
		private CompoundTag entityData;

		SummonEntityInstruction(JsonObject summonInstructionInput, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
			String positionArgumentString = "";
			if (summonInstructionInput.has("x") && summonInstructionInput.has("y") && summonInstructionInput.has("z")) {
				positionArgumentString = summonInstructionInput.get("x").getAsString() + " " + summonInstructionInput.get("y").getAsString() + " " + summonInstructionInput.get("z").getAsString();
			}
			String entityIDString = summonInstructionInput.has("id") ? summonInstructionInput.get("id").getAsString() : "";
			String entityDataString = summonInstructionInput.has("data") ? summonInstructionInput.get("data").getAsString() : "";

			parse(positionArgumentString + " " + entityIDString + " " + entityDataString, commandSourceStack);
		}

		SummonEntityInstruction(String inputData, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
			parse(inputData, commandSourceStack);
		}

		private void parse(String inputData, CommandSourceStack commandSourceStack) throws CommandSyntaxException {
			StringReader sr = new StringReader(inputData);

			entityPosition = Vec3Argument.vec3().parse(sr).getPosition(commandSourceStack);
			sr.skip();

			entityResourceLocation = EntitySummonArgument.id().parse(sr);
			sr.skip();

			try {
				String entityDataString = sr.getRemaining();
				if (entityDataString.isBlank()) {
					entityDataString = "{}";
				}
				entityData = TagParser.parseTag(entityDataString);
			} catch (StringIndexOutOfBoundsException e) {
				entityData = new CompoundTag();
			}
		}

		public String summon(ServerLevel level) {
			if (!Level.isInSpawnableBounds(new BlockPos(entityPosition))) {
				return "Position is not in spawnable bounds";
			}

			entityData.putString("id", entityResourceLocation.toString());

			Entity entity = EntityType.loadEntityRecursive(entityData, level, (_entity) -> {
				_entity.moveTo(entityPosition);
				return _entity;
			});
			if (entity == null) {
				return "Entity could not be spawned";
			}
			entity.checkDespawn();
			if (entity.isRemoved()) {
				return "Entity was removed right after spawn for reason: " + entity.getRemovalReason();
			}
			if (!level.tryAddFreshEntityWithPassengers(entity)) {
				return "Entity with this UUID already exists";
			}
			return entity.getStringUUID();
		}

	}

	/**
	 * Model to encapsulate parsing of data patches, finding existing entities with that {@link UUID}, applying the patch and returning a success/fail status.
	 */
	private final static class PatchEntityInstruction {
		private final UUID uuid;
		private final CompoundTag patchData;

		PatchEntityInstruction(JsonObject inputData) throws IllegalArgumentException, CommandSyntaxException, UnsupportedOperationException {
			uuid = UUID.fromString(inputData.get("uuid").getAsString());
			patchData = TagParser.parseTag(inputData.get("data").getAsString());
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

			CompoundTag patchedData = TagMerger.merge(entity.serializeNBT(), patchData);
			if (entity.serializeNBT().equals(patchedData)) {
				return false;
			}
			entity.load(patchedData);
			return true;
		}
	}
}

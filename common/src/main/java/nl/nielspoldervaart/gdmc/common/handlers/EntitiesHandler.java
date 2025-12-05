package nl.nielspoldervaart.gdmc.common.handlers;

import net.minecraft.commands.arguments.ResourceLocationArgument;
#if (MC_VER == MC_1_21_4 || MC_VER == MC_1_21_10)
import net.minecraft.world.entity.EntitySpawnReason;
#endif
#if (MC_VER == MC_1_21_10)
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
#endif
import nl.nielspoldervaart.gdmc.common.utils.TagUtils;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
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

	// GET: Search entities using a Target Selector (https://minecraft.wiki/w/Target_selectors).
	// Defaults to "@e[x=x,y=y,z=z,dx=dx,dy=dy,dz=dz]" (find all entities within area).
	private String entitySelectorString;

	// GET: Whether to include entity data https://minecraft.wiki/w/Entity_format#Entity_Format
	private boolean includeData;

	// GET/PUT/DELETE/PATCH: Search for entities within a specific dimension.
	// For GET requests this only works if selector string contains position arguments.
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

			entitySelectorString = queryParams.getOrDefault("selector", null);

			includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			throw new HttpException("Could not parse query parameter: " + e.getMessage(), 400);
		}

		String method = httpExchange.getRequestMethod().toLowerCase();

		JsonArray response;

		switch (method) {
			case "put" -> response = putEntitiesHandler(httpExchange.getRequestBody());
			case "get" -> response = getEntitiesHandler();
			case "delete" -> response = deleteEntitiesHandler(httpExchange.getRequestBody());
			case "patch" -> response = patchEntitiesHandler(httpExchange.getRequestBody());
			default -> throw new HttpException("Method not allowed. Only PUT, GET, DELETE and PATCH requests are supported.", 405);
		}

		// Response headers
		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		resolveRequest(httpExchange, response.toString());
	}

	/**
	 * @param requestBody request body of entity summon instructions
	 * @return summon results
	 */
	private JsonArray putEntitiesHandler(InputStream requestBody) {
		ServerLevel serverLevel = getServerLevel(dimension);
		CommandSourceStack cmdSrc = createCommandSource("GDMC-EntitiesHandler", serverLevel, new Vec3(x, y, z));

		JsonArray returnValues = new JsonArray();
		JsonArray entityDescriptionList = parseJsonArray(requestBody);

		for (JsonElement entityDescription : entityDescriptionList) {
			SummonEntityInstruction summonEntityInstruction;
			try {
				JsonObject json = entityDescription.getAsJsonObject();
				summonEntityInstruction = new SummonEntityInstruction(json, cmdSrc);
			} catch (UnsupportedOperationException | IllegalStateException | CommandSyntaxException e) {
				returnValues.add(instructionStatus(false, e.getMessage()));
				continue;
			}
			returnValues.add(summonEntityInstruction.summon(serverLevel));
		}

		return returnValues;
	}

	/**
	 * @return list of entity information
	 */
	private JsonArray getEntitiesHandler() {

		StringReader entitySelectorStringReader = new StringReader(
			entitySelectorString != null && !entitySelectorString.isBlank() ?
				entitySelectorString :
				"@e[x=%s,y=%s,z=%s,dx=%s,dy=%s,dz=%s]".formatted(x, y, z, dx, dy, dz)
		);
		try {
			EntitySelector entitySelector = EntityArgument.entities().parse(entitySelectorStringReader);
			ServerLevel serverLevel = getServerLevel(dimension);
			CommandSourceStack cmdSrc = createCommandSource(
				"GDMC-EntitiesHandler",
				serverLevel,
				new Vec3(x, y, z)
			);
			List<? extends Entity> entityList = entitySelector.findEntities(cmdSrc);

			JsonArray returnList = new JsonArray();
			for (Entity entity : entityList) {
				String entityId = TagUtils.getEntityId(entity);
				if (entityId == null) {
					continue;
				}
				JsonObject json = new JsonObject();
				json.addProperty("uuid", entity.getStringUUID());
				if (includeData) {
					json.addProperty("data", TagUtils.tagAsString(TagUtils.serializeEntityNBT(entity)));
				}
				returnList.add(json);
			}
			return returnList;
		} catch (CommandSyntaxException e) {
			throw new HttpException("Malformed entity target selector: " + e.getMessage(), 400);
		}

	}

	/**
	 * @param requestBody request body of entity removal instructions
	 * @return entity removal results
	 */
	private JsonArray deleteEntitiesHandler(InputStream requestBody) {

		ServerLevel level = getServerLevel(dimension);

		JsonArray returnValues = new JsonArray();

		JsonArray jsonListUUID = parseJsonArray(requestBody);

		for (JsonElement jsonElement : jsonListUUID) {
			String stringUUID;
			try {
				stringUUID = jsonElement.getAsString();
			} catch (UnsupportedOperationException | IllegalStateException e) {
				returnValues.add(instructionStatus(false, "Invalid UUID"));
				continue;
			}
			if (stringUUID.isBlank()) {
				returnValues.add(instructionStatus(false, "Invalid UUID"));
				continue;
			}
			Entity entityToBeRemoved;
			try {
				entityToBeRemoved = level.getEntity(UUID.fromString(stringUUID));
			} catch (IllegalArgumentException e) {
				returnValues.add(instructionStatus(false, e.getMessage()));
				continue;
			}

			if (entityToBeRemoved != null) {
				if (entityToBeRemoved.isRemoved()) {
					returnValues.add(instructionStatus(false));
				} else {
					entityToBeRemoved.remove(Entity.RemovalReason.DISCARDED);
					returnValues.add(instructionStatus(true));
				}
				continue;
			}
			returnValues.add(instructionStatus(false));
		}
		return returnValues;
	}

	/**
	 * @param requestBody request body of entity patch instructions
	 * @return entity patch status results
	 */
	private JsonArray patchEntitiesHandler(InputStream requestBody) {

		ServerLevel level = getServerLevel(dimension);

		JsonArray returnValues = new JsonArray();

		JsonArray jsonList = parseJsonArray(requestBody);

		for (JsonElement entityDescription : jsonList) {
			PatchEntityInstruction patchEntityInstruction;
			try {
				JsonObject json = entityDescription.getAsJsonObject();
				patchEntityInstruction = new PatchEntityInstruction(json);
			} catch (IllegalStateException | UnsupportedOperationException | NullPointerException | IllegalArgumentException | CommandSyntaxException e) {
				returnValues.add(instructionStatus(false, e.getMessage()));
				continue;
			}
			try {
				if (patchEntityInstruction.applyPatch(level)) {
					returnValues.add(instructionStatus(true));
					continue;
				}
			} catch (ReportedException e) {
				returnValues.add(instructionStatus(false, e.getMessage()));
				continue;
			}
			returnValues.add(instructionStatus(false));
		}

		return returnValues;
	}

	private final static class SummonEntityInstruction {

		private final ResourceLocation entityResourceLocation;
		private final Vec3 entityPosition;
		private final CompoundTag entityData;

		SummonEntityInstruction(JsonObject summonInstructionInput, CommandSourceStack commandSourceStack) throws CommandSyntaxException, IllegalStateException, UnsupportedOperationException {
			String positionArgumentString = "";
			if (summonInstructionInput.has("x") && summonInstructionInput.has("y") && summonInstructionInput.has("z")) {
				positionArgumentString = summonInstructionInput.get("x").getAsString() + " " + summonInstructionInput.get("y").getAsString() + " " + summonInstructionInput.get("z").getAsString();
			}
			String entityIDString = summonInstructionInput.has("id") ? summonInstructionInput.get("id").getAsString() : "";
			String entityDataString = summonInstructionInput.has("data") ? summonInstructionInput.get("data").getAsString() : "";

			StringReader sr = new StringReader(positionArgumentString + " " + entityIDString + " " + entityDataString);

			entityPosition = Vec3Argument.vec3().parse(sr).getPosition(commandSourceStack);
			sr.skip();

			entityResourceLocation = ResourceLocationArgument.id().parse(sr);
			sr.skip();

			entityData = TagUtils.parseTag(sr.getRemaining());
		}

		public JsonObject summon(ServerLevel level) {

			if (!Level.isInSpawnableBounds(new BlockPos(
				(int)entityPosition.x,
				(int)entityPosition.y,
				(int)entityPosition.z
			))) {
				return instructionStatus(false, "Position is not in spawnable bounds");
			}

			entityData.putString("id", entityResourceLocation.toString());

			#if (MC_VER == MC_1_21_4 || MC_VER == MC_1_21_10)
			Entity entity = EntityType.loadEntityRecursive(entityData, level, EntitySpawnReason.COMMAND,(_entity) -> {
				_entity.setPos(entityPosition);
				return _entity;
			});
			#else
			Entity entity = EntityType.loadEntityRecursive(entityData, level, (_entity) -> {
				_entity.setPos(entityPosition);
				return _entity;
			});
			#endif
			if (entity == null) {
				return instructionStatus(false, "Entity could not be spawned");
			}
			entity.checkDespawn();
			if (entity.isRemoved()) {
				return instructionStatus(false, "Entity was removed right after spawn for reason: " + entity.getRemovalReason());
			}
			if (!level.tryAddFreshEntityWithPassengers(entity)) {
				return instructionStatus(false, "Entity with this UUID already exists");
			}
			return instructionStatus(true, entity.getStringUUID());
		}

	}

	/**
	 * Model to encapsulate parsing of data patches, finding existing entities with that {@link UUID}, applying the patch and returning a success/fail status.
	 */
	private final static class PatchEntityInstruction {
		private final UUID uuid;
		private final CompoundTag patchData;

		PatchEntityInstruction(JsonObject inputData) throws IllegalArgumentException, CommandSyntaxException, UnsupportedOperationException, NullPointerException {
			uuid = UUID.fromString(inputData.get("uuid").getAsString());
			patchData = TagUtils.parseTag(inputData.get("data").getAsString());
		}

		public boolean applyPatch(ServerLevel level) throws ReportedException {
			if (uuid == null) {
				return false;
			}
			Entity entity = level.getEntity(uuid);
			if (entity == null) {
				return false;
			}

			CompoundTag patchedData = TagUtils.mergeTags(TagUtils.serializeEntityNBT(entity), patchData);
			if (TagUtils.serializeEntityNBT(entity).equals(patchedData)) {
				return false;
			}
			#if (MC_VER == MC_1_21_10)
			ValueInput valueInput = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), patchedData);
			entity.load(valueInput);
			#else
			entity.load(patchedData);
			#endif
			return true;
		}
	}
}

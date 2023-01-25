package com.gdmc.httpinterfacemod.utils;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntitySummonArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class SummonCommandArgumentsBuilder {

	private ResourceLocation entityResourceId;
	private CommandSyntaxException missingEntityResourceException;
	private final Vec3 entityPosition;
	private CompoundTag entityData;

	public SummonCommandArgumentsBuilder(CommandSourceStack commandSourceStack, JsonObject json) {
		try {
			String entityName = json.has("id") ? json.get("id").getAsString() : "";
			entityResourceId = getEntityResourceFromString(new StringReader(entityName));
		} catch (CommandSyntaxException commandSyntaxException) {
			missingEntityResourceException = commandSyntaxException;
		}
		Vec3 referencePosition = commandSourceStack.getPosition();
		String posXString = json.has("x") ? json.get("x").getAsString() : String.valueOf(referencePosition.x);
		String posYString = json.has("y") ? json.get("y").getAsString() : String.valueOf(referencePosition.y);
		String posZString = json.has("z") ? json.get("z").getAsString() : String.valueOf(referencePosition.z);
		entityPosition = getVec3FromString(commandSourceStack, new StringReader("%s %s %s".formatted(posXString, posYString, posZString)));
		if (json.has("data")) {
			entityData = getEntityCompoundTagFromString(new StringReader(json.get("data").toString()));
		}
	}

	public SummonCommandArgumentsBuilder(CommandSourceStack commandSourceStack, String str) {
		StringReader sr = new StringReader(str);

		entityPosition = getVec3FromString(commandSourceStack, sr);
		try {
			entityResourceId = getEntityResourceFromString(sr);
		} catch (CommandSyntaxException commandSyntaxException) {
			missingEntityResourceException = commandSyntaxException;
		}
		entityData = getEntityCompoundTagFromString(sr);
	}

	public String getResourceIdArgument() {
		if (missingEntityResourceException != null) {
			return missingEntityResourceException.getMessage();
		}
		return entityResourceId.toString();
	}

	public String getPositionArgument() {
		return "%s %s %s".formatted(entityPosition.x, entityPosition.y, entityPosition.z);
	}

	public String getDataArgument() {
		if (entityData == null) {
			return null;
		}
		return entityData.toString();
	}

	private static ResourceLocation getEntityResourceFromString(StringReader sr) throws CommandSyntaxException {
		return EntitySummonArgument.id().parse(sr);
	}

	private static Vec3 getVec3FromString(CommandSourceStack commandSourceStack, StringReader sr) {
		try {
			Coordinates coordinates = Vec3Argument.vec3().parse(sr);
			sr.skip();
			return coordinates.getPosition(commandSourceStack);
		} catch (CommandSyntaxException commandSyntaxException) {
			return commandSourceStack.getPosition();
		}
	}

	private static CompoundTag getEntityCompoundTagFromString(StringReader sr) {
		try {
			return CompoundTagArgument.compoundTag().parse(sr);
		} catch (CommandSyntaxException commandSyntaxException) {
			return null;
		}
	}

}

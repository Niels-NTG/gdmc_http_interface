package com.gdmc.httpinterfacemod.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class StructureHandler extends HandlerBase {
	public StructureHandler(MinecraftServer mcServer) {
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

		String mirror;
		int rotation;
		int pivotX;
		int pivotY;
		int pivotZ;

		String dimension;

		try {
			x = Integer.parseInt(queryParams.getOrDefault("x", "0"));
			y = Integer.parseInt(queryParams.getOrDefault("y", "0"));
			z = Integer.parseInt(queryParams.getOrDefault("z", "0"));

			dx = Integer.parseInt(queryParams.getOrDefault("dx", "1"));
			dy = Integer.parseInt(queryParams.getOrDefault("dy", "1"));
			dz = Integer.parseInt(queryParams.getOrDefault("dz", "1"));

			mirror = queryParams.getOrDefault("mirror", "");
			rotation = Integer.parseInt(queryParams.getOrDefault("rotate", "0")) % 4;
			pivotX = Integer.parseInt(queryParams.getOrDefault("pivotx", "0"));
			pivotY = Integer.parseInt(queryParams.getOrDefault("pivoty", "0"));
			pivotZ = Integer.parseInt(queryParams.getOrDefault("pivotz", "0"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			String message = "Could not parse query parameter: " + e.getMessage();
			throw new HandlerBase.HttpException(message, 400);
		}

		// if content type is application/json use that otherwise return text
		Headers reqestHeaders = httpExchange.getRequestHeaders();
		String contentType = getHeader(reqestHeaders, "Accept", "*/*");
		boolean returnJson = contentType.equals("application/json") || contentType.equals("text/json");

		String method = httpExchange.getRequestMethod().toLowerCase();
		String responseString;

		if (method.equals("post")) {
			CompoundTag structureCompound;
			try {
				// Read request body into NBT data compound that can be placed in the world.
				InputStream bodyStream = httpExchange.getRequestBody();
				structureCompound = NbtIo.readCompressed(bodyStream);
			} catch (Exception exception) {
				String message = "Could not process request body: " + exception.getMessage();
				throw new HandlerBase.HttpException(message, 400);
			}

			// Prepare transformations to the structure
			StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings();
			switch (mirror) {
				case "x" -> structurePlaceSettings.setMirror(Mirror.FRONT_BACK);
				case "z" -> structurePlaceSettings.setMirror(Mirror.LEFT_RIGHT);
			}
			switch (rotation) {
				case 1 -> structurePlaceSettings.setRotation(Rotation.CLOCKWISE_90);
				case 2 -> structurePlaceSettings.setRotation(Rotation.CLOCKWISE_180);
				case 3 -> structurePlaceSettings.setRotation(Rotation.COUNTERCLOCKWISE_90);
			}
			structurePlaceSettings.setRotationPivot(new BlockPos(pivotX, pivotY, pivotZ));

			ServerLevel serverLevel = getServerLevel(dimension);

			try {
				boolean hasPlaced = serverLevel.getStructureManager().readStructure(structureCompound).placeInWorld(
					serverLevel,
					new BlockPos(x, y, z),
					new BlockPos(x, y, z),
					structurePlaceSettings,
					serverLevel.getRandom(),
					BlocksHandler.getBlockFlags(true, false)
				);
				if (hasPlaced) {
					responseString = "1";
				} else {
					responseString = "0";
				}
			} catch (Exception exception) {
				String message = "Could place structure: " + exception.getMessage();
				throw new HandlerBase.HttpException(message, 500);
			}
		} else {
			throw new HandlerBase.HttpException("Method not allowed. Only POST requests are supported.", 405);
		}

		resolveRequest(httpExchange, responseString);
	}
}

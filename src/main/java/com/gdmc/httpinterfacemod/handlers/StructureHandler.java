package com.gdmc.httpinterfacemod.handlers;

import com.gdmc.httpinterfacemod.utils.JsonTagVisitor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

public class StructureHandler extends HandlerBase {

	String dimension;
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
		boolean includeEntities;

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
			includeEntities = Boolean.parseBoolean(queryParams.getOrDefault("entities", "false"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			String message = "Could not parse query parameter: " + e.getMessage();
			throw new HandlerBase.HttpException(message, 400);
		}

		// with this header we return pure NBT binary
		// if content type is application/json use that otherwise return text
		Headers requestHeaders = httpExchange.getRequestHeaders();
		String acceptHeader = getHeader(requestHeaders, "Accept", "*/*");
		boolean returnPlainText = acceptHeader.equals("text/plain");
		boolean returnJson = hasJsonTypeInHeader(acceptHeader);
		String acceptEncodingHeader = getHeader(requestHeaders, "Accept-Encoding", "gzip");
		boolean returnCompressed = acceptEncodingHeader.equals("gzip");

		String method = httpExchange.getRequestMethod().toLowerCase();
		String responseString;

		if (method.equals("post")) {
			String contentEncodingHeader = getHeader(requestHeaders, "Content-Encoding", "*");
			boolean inputShouldBeCompressed = contentEncodingHeader.equals("gzip");

			CompoundTag structureCompound;
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			httpExchange.getRequestBody().transferTo(outputStream);
			try {
				// Read request body into NBT data compound that can be placed in the world.
				structureCompound = NbtIo.readCompressed(new ByteArrayInputStream(outputStream.toByteArray()));
			} catch (Exception exception) {
				// If header states the content should be compressed but it isn't, throw an error. Otherwise, try
				// reading the content again, assuming it is not compressed.
				if (inputShouldBeCompressed) {
					String message = "Could not process request body: " + exception.getMessage();
					throw new HttpException(message, 400);
				}
				try {
					DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
					structureCompound = NbtIo.read(dataInputStream);
				} catch (Exception exception1) {
					String message = "Could not process request body: " + exception1.getMessage();
					throw new HttpException(message, 400);
				}
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

			structurePlaceSettings.setIgnoreEntities(!includeEntities);

			ServerLevel serverLevel = getServerLevel(dimension);

			try {

				StructureTemplate structureTemplate = serverLevel.getStructureManager().readStructure(structureCompound);

				BlockPos origin = new BlockPos(x, y, z);

				boolean hasPlaced = structureTemplate.placeInWorld(
					serverLevel,
					origin,
					origin,
					structurePlaceSettings,
					serverLevel.getRandom(),
					BlocksHandler.getBlockFlags(true, false)
				);
				if (hasPlaced) {
					ListTag blockList = structureCompound.getList("blocks", Tag.TAG_COMPOUND);
					for (int i = 0; i < blockList.size(); i++) {
						CompoundTag tag = blockList.getCompound(i);
						if (tag.contains("nbt")) {
							ListTag posTag = tag.getList("pos", Tag.TAG_INT);
							BlockPos blockPosInStructure = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
							BlockPos transformedPosition = StructureTemplate.calculateRelativePosition(structurePlaceSettings, blockPosInStructure);
							BlockPos transformedGlobalBlockPos = origin.offset(transformedPosition);

							BlockEntity existingBlockEntity = serverLevel.getExistingBlockEntity(transformedGlobalBlockPos);
							if (existingBlockEntity != null) {
								existingBlockEntity.deserializeNBT(tag.getCompound("nbt"));
							}
						}
					}
					responseString = "1";
				} else {
					responseString = "0";
				}
			} catch (Exception exception) {
				String message = "Could place structure: " + exception.getMessage();
				throw new HttpException(message, 400);
			}

			Headers responseHeaders = httpExchange.getResponseHeaders();
			if (returnJson) {
				responseString = "[\"" + responseString + "\"]";
				addResponseHeaderContentTypeJson(responseHeaders);
			} else {
				addResponseHeaderContentTypePlain(responseHeaders);
			}
			resolveRequest(httpExchange, responseString);
		} else if (method.equals("get")) {
			ServerLevel serverLevel = getServerLevel(dimension);

			int xOffset = x + dx;
			int xMin = Math.min(x, xOffset);

			int yOffset = y + dy;
			int yMin = Math.min(y, yOffset);

			int zOffset = z + dz;
			int zMin = Math.min(z, zOffset);

			StructureTemplate structureTemplate = new StructureTemplate();
			BlockPos origin = new BlockPos(xMin, yMin, zMin);
			Vec3i size = new Vec3i(Math.abs(dx), Math.abs(dy), Math.abs(dz));
			structureTemplate.fillFromWorld(
				serverLevel,
				origin,
				size,
				includeEntities,
				null
			);

			CompoundTag newStructureCompoundTag = structureTemplate.save(new CompoundTag());
			ListTag blockList = newStructureCompoundTag.getList("blocks", Tag.TAG_COMPOUND);
			for (int i = 0; i < blockList.size(); i++) {
				CompoundTag tag = blockList.getCompound(i);
				if (tag.contains("nbt") || !tag.contains("pos")) {
					continue;
				}
				ListTag posTag = tag.getList("pos", Tag.TAG_INT);
				BlockPos blockPosInStructure = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
				BlockPos blockPosInWorld = origin.offset(blockPosInStructure);

				BlockEntity existingBlockEntity = serverLevel.getExistingBlockEntity(blockPosInWorld);
				if (existingBlockEntity != null) {
					CompoundTag blockEntityCompoundTag = existingBlockEntity.saveWithoutMetadata();
					tag.put("nbt", blockEntityCompoundTag);
				}

			}

			// Response header and response body
			Headers responseHeaders = httpExchange.getResponseHeaders();
			if (returnPlainText) {
				addResponseHeaderContentTypePlain(responseHeaders);

				responseString = newStructureCompoundTag.toString();

				resolveRequest(httpExchange, responseString);
			} else if (returnJson) {
				addResponseHeaderContentTypeJson(responseHeaders);

				JsonObject tagsAsJsonObject = JsonParser.parseString(new JsonTagVisitor().visit(newStructureCompoundTag)).getAsJsonObject();
				responseString = new Gson().toJson(tagsAsJsonObject);

				resolveRequest(httpExchange, responseString);
			} else {
				addResponseHeaderContentTypeBinary(responseHeaders);

				ByteArrayOutputStream boas = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(boas);
				if (returnCompressed) {
					responseHeaders.add("Content-Encoding", "gzip");
					NbtIo.writeCompressed(newStructureCompoundTag, dos);
				} else {
					NbtIo.write(newStructureCompoundTag, dos);
				}
				dos.flush();
				byte[] responseBytes = boas.toByteArray();

				resolveRequest(httpExchange, responseBytes);
			}

		} else {
			throw new HttpException("Method not allowed. Only POST and GET requests are supported.", 405);
		}
	}
}

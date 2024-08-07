package nl.nielspoldervaart.gdmc.common.handlers;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPOutputStream;

public class StructureHandler extends HandlerBase {

	// POST/GET: x, y, z positions
	private int x;
	private int y;
	private int z;

	// GET: Ranges in the x, y, z directions (can be negative). Defaults to 1.
	private int dx;
	private int dy;
	private int dz;

	// POST: If set, mirror the input structure on the x or z axis. Valid values are "x", "z" and unset.
	private String mirror;

	// POST: If set, rotate the input structure 0, 1, 2, 3 times in 90° clock-wise.
	private int rotate;

	// POST: set pivot point for the rotation. Values are relative to origin of the structure.
	private int pivotX;
	private int pivotZ;

	// POST/GET: Whether to include entities (mobs, villagers, items) in placing/getting a structure.
	private boolean includeEntities;

	// POST: Defaults to true. If true, update neighbouring blocks after placement.
	private boolean doBlockUpdates;

	// POST: Defaults to false. If true, block updates cause item drops after placement.
	private boolean spawnDrops;

	// POST: Defaults to true. If false, remove water sources already present at placement location of structure.
	private boolean keepLiquids;

	// POST: Overrides both doBlockUpdates and spawnDrops if set. For more information see #getBlockFlags and
	// https://minecraft.wiki/w/Block_update
	private int customFlags; // -1 == no custom flags
	private String dimension;

	// POST/GET: is true, constrain placement/getting blocks within the current build area.
	private boolean withinBuildArea;

	public StructureHandler(MinecraftServer mcServer) {
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

			mirror = queryParams.getOrDefault("mirror", "");

			rotate = Integer.parseInt(queryParams.getOrDefault("rotate", "0")) % 4;

			pivotX = Integer.parseInt(queryParams.getOrDefault("pivotx", "0"));
			pivotZ = Integer.parseInt(queryParams.getOrDefault("pivotz", "0"));
			includeEntities = Boolean.parseBoolean(queryParams.getOrDefault("entities", "false"));

			doBlockUpdates = Boolean.parseBoolean(queryParams.getOrDefault("doBlockUpdates", "true"));

			spawnDrops = Boolean.parseBoolean(queryParams.getOrDefault("spawnDrops", "false"));

			customFlags = Integer.parseInt(queryParams.getOrDefault("customFlags", "-1"), 2);

			withinBuildArea = Boolean.parseBoolean(queryParams.getOrDefault("withinBuildArea", "false"));

			keepLiquids = Boolean.parseBoolean(queryParams.getOrDefault("keepLiquids", "true"));

			dimension = queryParams.getOrDefault("dimension", null);
		} catch (NumberFormatException e) {
			throw new HttpException("Could not parse query parameter: " + e.getMessage(), 400);
		}

		Headers requestHeaders = httpExchange.getRequestHeaders();

		StructureEncoding encoding = StructureEncoding.NBT_UNCOMPRESSED;

		switch (httpExchange.getRequestMethod().toLowerCase()) {
			case "post" -> {
				// Check if there is a header present stating that the request body is compressed with GZIP.
				// Any structure file generated by Minecraft itself using the Structure Block
				// (https://minecraft.wiki/w/Structure_Block) as well as the built-in Structures are
				// stored in this compressed format.
				// If content encoding is set to "text/plain", the request body is expected to be
				// SNBT format (https://minecraft.wiki/w/NBT_format#SNBT_format).
				String contentEncodingHeader = getHeader(requestHeaders, "Content-Encoding", "*");
				switch (contentEncodingHeader) {
					case "gzip" -> encoding = StructureEncoding.NBT_COMPRESSED;
					case "text/plain" -> encoding = StructureEncoding.SNBT;
				}
				postStructureHandler(httpExchange, encoding);
			}
			case "get" -> {
				// If accept header has "text/plain", return structure file as an SNBT string.
				String acceptHeader = getHeader(requestHeaders, "Accept", "application/octet-stream");
				if (acceptHeader.equals("text/plain")) {
					encoding = StructureEncoding.SNBT;
				} else {
					// If not, assume the file is in binary format. If "Accept-Encoding" header is set to "gzip"
					// (both default) compress the result using GZIP before sending out the response.
					String acceptEncodingHeader = getHeader(requestHeaders, "Accept-Encoding", "gzip");
					if (acceptEncodingHeader.contains("gzip")) {
						encoding = StructureEncoding.NBT_COMPRESSED;
					}
				}
				getStructureHandler(httpExchange, encoding);
			}
			default -> throw new HttpException("Method not allowed. Only POST and GET requests are supported.", 405);
		}
	}

	private void postStructureHandler(HttpExchange httpExchange, StructureEncoding encoding) throws IOException {
		JsonObject responseValue;

		CompoundTag structureCompound;
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			httpExchange.getRequestBody().transferTo(outputStream);
			if (outputStream.size() == 0) {
				throw new HttpException("Request body is empty", 400);
			}
		} catch (IOException e1) {
			throw new HttpException("Could not process request body: " + e1.getMessage(), 400);
		}
		if (encoding == StructureEncoding.NBT_COMPRESSED || encoding == StructureEncoding.NBT_UNCOMPRESSED) {
			try {
				// Read request body into NBT data compound that can be placed in the world.
				structureCompound = NbtIo.readCompressed(new ByteArrayInputStream(outputStream.toByteArray()));
			} catch (IOException e2) {
				// If header states the content should be compressed but it isn't, throw an error. Otherwise, try
				// reading the content again, assuming it is not compressed.
				if (encoding == StructureEncoding.NBT_COMPRESSED) {
					throw new HttpException("Could not process request body: " + e2.getMessage(), 400);
				}
				try {
					DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
					structureCompound = NbtIo.read(dataInputStream);
				} catch (IOException e3) {
					throw new HttpException("Could not process request body: " + e3.getMessage(), 400);
				}
			}
		} else {
			try {
				structureCompound = TagParser.parseTag(outputStream.toString());
			} catch (CommandSyntaxException e4) {
				throw new HttpException("Could not process request body: " + e4.getMessage(), 400);
			}
		}

		// Prepare transformation settings for the structure.
		StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings();
		switch (mirror) {
			case "x" -> structurePlaceSettings.setMirror(Mirror.FRONT_BACK);
			case "z" -> structurePlaceSettings.setMirror(Mirror.LEFT_RIGHT);
		}
		switch (rotate) {
			case 1 -> structurePlaceSettings.setRotation(Rotation.CLOCKWISE_90);
			case 2 -> structurePlaceSettings.setRotation(Rotation.CLOCKWISE_180);
			case 3 -> structurePlaceSettings.setRotation(Rotation.COUNTERCLOCKWISE_90);
		}
		structurePlaceSettings.setRotationPivot(new BlockPos(pivotX, 0, pivotZ));
		structurePlaceSettings.setIgnoreEntities(!includeEntities);
		structurePlaceSettings.setKeepLiquids(keepLiquids);

		ServerLevel serverLevel = getServerLevel(dimension);

		try {
			// Create a StructureTemplate using the CompoundTag constructed from the user input as the data source.
			StructureTemplate structureTemplate = serverLevel.getStructureManager().readStructure(structureCompound);

			BlockPos origin = new BlockPos(x, y, z);
			BoundingBox box = structureTemplate.getBoundingBox(structurePlaceSettings, origin);
			if (BuildArea.isOutsideBuildArea(box, withinBuildArea)) {
				throw new HttpException("Could not place structure: bounds of structure are (partially) outside the build area.", 403);
			}
			int blockPlacementFlags = customFlags >= 0 ? customFlags : BlocksHandler.getBlockFlags(doBlockUpdates, spawnDrops);

			// If placement should not spawn drops, make sure any entities at that location are cleared to prevent items
			// (eg. contents of a chest) from dropping.
			ArrayList<BlockPos> positionsToClear = new ArrayList<>();
			if ((blockPlacementFlags & Block.UPDATE_SUPPRESS_DROPS) != 0) {
				structureCompound.getList("blocks", CompoundTag.TAG_COMPOUND).forEach(entry -> {
					CompoundTag tag = (CompoundTag) entry;
					if (tag.contains("pos")) {
						ListTag posTag = tag.getList("pos", CompoundTag.TAG_INT);
						positionsToClear.add(origin.offset(StructureTemplate.calculateRelativePosition(
							structurePlaceSettings,
							new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2))
						)));
					}
				});
			}

			// Place the structure into the world on the server thread to ensure NBT data within the blocks of the structure
			// get placed at the same time as the blocks themselves.
			CompletableFuture<Boolean> hasPlacedFuture = mcServer.submit(() -> {
				if (!positionsToClear.isEmpty()) {
					for (BlockPos pos : positionsToClear) {
						BlockEntity blockEntityToClear = BlocksHandler.getExistingBlockEntity(pos, serverLevel);
						Clearable.tryClear(blockEntityToClear);
					}
				}

				return structureTemplate.placeInWorld(
					serverLevel,
					origin,
					origin,
					structurePlaceSettings,
					serverLevel.getRandom(),
					blockPlacementFlags
				);
			});

			// Wait for the placement to resolve and create a response status.
			boolean hasPlaced = hasPlacedFuture.get();
			if (hasPlaced) {
				responseValue = instructionStatus(true);
			} else {
				responseValue = instructionStatus(false);
			}
		} catch (InterruptedException | ExecutionException exception) {
			throw new HttpException("Could not place structure: " + exception.getMessage(), 400);
		}

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		resolveRequest(httpExchange, responseValue.toString());
	}

	private void getStructureHandler(HttpExchange httpExchange, StructureEncoding encoding) throws IOException {

		ServerLevel serverLevel = getServerLevel(dimension);

		// Calculate boundaries of area of blocks to gather information on.
		BoundingBox initialBox = createBoundingBox(x, y, z, dx, dy, dz);
		BoundingBox box = BuildArea.clampToBuildArea(initialBox, withinBuildArea);

		// Create StructureTemplate using blocks within the given area of the world.
		StructureTemplate structureTemplate = new StructureTemplate();
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		Vec3i size = new BlockPos(box.getXSpan(), box.getYSpan(), box.getZSpan());

		// Fill the structure template with blocks in the given area of the level. Do this on the server thread such that NBT block data
		// gets gathered correctly. When resolved, save the result to a new CompoundTag.
		CompletableFuture<Void> placeInWorldFuture = mcServer.submit(() -> structureTemplate.fillFromWorld(
			serverLevel,
			origin,
			size,
			includeEntities,
			null
		));
		placeInWorldFuture.join();
		CompoundTag newStructureCompoundTag = structureTemplate.save(new CompoundTag());

		Headers responseHeaders = httpExchange.getResponseHeaders();
		setDefaultResponseHeaders(responseHeaders);

		// Serialize CompoundTag to SNBT format.
		if (encoding == StructureEncoding.SNBT) {
			String responseString = newStructureCompoundTag.toString();

			setResponseHeadersContentTypePlain(responseHeaders);
			resolveRequest(httpExchange, responseString);
			return;
		}

		// Create gzipped version of the binary NBT data.
		boolean returnCompressed = encoding == StructureEncoding.NBT_COMPRESSED;
		setResponseHeadersContentTypeBinary(responseHeaders, returnCompressed);

		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		if (returnCompressed) {
			GZIPOutputStream dos = new GZIPOutputStream(boas);
			NbtIo.writeCompressed(newStructureCompoundTag, dos);
			dos.flush();
			byte[] responseBytes = boas.toByteArray();

			resolveRequest(httpExchange, responseBytes);
			return;
		}
		DataOutputStream dos = new DataOutputStream(boas);
		NbtIo.write(newStructureCompoundTag, dos);
		dos.flush();
		byte[] responseBytes = boas.toByteArray();

		resolveRequest(httpExchange, responseBytes);
	}

	private enum StructureEncoding {
		NBT_UNCOMPRESSED,
		NBT_COMPRESSED,
		SNBT
	}
}
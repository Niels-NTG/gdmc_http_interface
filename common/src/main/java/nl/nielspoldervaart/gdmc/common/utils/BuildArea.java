package nl.nielspoldervaart.gdmc.common.utils;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nl.nielspoldervaart.gdmc.common.handlers.HandlerBase.HttpException;

import java.util.HashMap;

public class BuildArea {

	private static BuildAreaInstance currentBuildAreaInstance;

	private static final Identifier buildAreaStorageIdentifier = Identifier.parse("gdmc:buildarea");

	public static BuildAreaInstance getCurrentBuildArea() {
		if (currentBuildAreaInstance == null) {
			throw new HttpException("No build area is specified. Use the \"/buildarea set\" command inside Minecraft to set a build area.", 404);
		}
		return currentBuildAreaInstance;
	}

	public static BuildAreaInstance setCurrentBuildArea(BlockPos from, BlockPos to, MinecraftServer server, BlockPos entityPosition, String name) {
		currentBuildAreaInstance = new BuildAreaInstance(name, from, to, entityPosition);
		saveBuildAreaToStorage(server, name, currentBuildAreaInstance);
		return currentBuildAreaInstance;
	}

	private static void saveBuildAreaToStorage(MinecraftServer server, String name, BuildAreaInstance buildArea) {
		CompoundTag buildAreaStorageData = getStorageData(server);
		CompoundTag positionTag = new CompoundTag();
		positionTag.putLong("from", buildArea.from.asLong());
		positionTag.putLong("to", buildArea.to.asLong());
		positionTag.putLong("spawn", buildArea.spawnPos.asLong());
		buildAreaStorageData.put(name, positionTag);
		server.getCommandStorage().set(buildAreaStorageIdentifier, buildAreaStorageData);
	}

	public static BuildAreaInstance setCurrentBuildAreaFromStorage(MinecraftServer server, String name) {
		if (getStorageData(server).contains(name)) {
			BuildAreaInstance newBuildArea = createBuildAreaInstanceFromData(server, name);
			if (newBuildArea == null) {
				return null;
			}
			currentBuildAreaInstance = newBuildArea;
			return newBuildArea;
		}
		return null;
	}

	private static CompoundTag getStorageData(MinecraftServer server) {
		return server.getCommandStorage().get(buildAreaStorageIdentifier);
	}

	public static BuildAreaInstance createBuildAreaInstanceFromData(MinecraftServer server, String name) {
		if (getStorageData(server).contains(name)) {
			CompoundTag buildAreaStorageData = getStorageData(server).getCompoundOrEmpty(name);
			return new BuildAreaInstance(
				name,
				BlockPos.of(buildAreaStorageData.getLongOr("from", 0)),
				BlockPos.of(buildAreaStorageData.getLongOr("to", 0)),
				BlockPos.of(buildAreaStorageData.getLongOr("spawn", 0))
			);
		}
		return null;
	}

	public static boolean unsetBuildArea(MinecraftServer server, String name) {
		if (currentBuildAreaInstance != null && currentBuildAreaInstance.name.equals(name)) {
			currentBuildAreaInstance = null;
		}
		if (getStorageData(server).contains(name)) {
			removeBuildAreaFromStorage(server, name);
			return true;
		}
		return false;
	}

	private static void removeBuildAreaFromStorage(MinecraftServer server, String name) {
		CompoundTag buildAreaStorageData = getStorageData(server);
		buildAreaStorageData.remove(name);
		server.getCommandStorage().set(buildAreaStorageIdentifier, buildAreaStorageData);
	}

	public static boolean isOutsideBuildArea(BlockPos blockPos, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getCurrentBuildArea().isOutsideBuildArea(blockPos.getX(), blockPos.getZ());
		}
		return false;
	}

	public static boolean isOutsideBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return !getCurrentBuildArea().isInsideBuildArea(box);
		}
		return false;
	}

	public static BoundingBox clampToBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getCurrentBuildArea().clampBox(box);
		}
		return box;
	}

	public static BoundingBox clampChunksToBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getCurrentBuildArea().clampSectionBox(box);
		}
		return box;
	}

	public static HashMap<String, BuildAreaInstance> getSavedBuildAreas(MinecraftServer server) {
		HashMap<String, BuildAreaInstance> savedBuildAreas = new HashMap<>();
		for (String name : getStorageData(server).keySet()) {
			savedBuildAreas.put(name, createBuildAreaInstanceFromData(server, name));
		}
		return savedBuildAreas;
	}

	public static String toJSONString() {
		if (currentBuildAreaInstance == null) {
			return "";
		}
		return getCurrentBuildArea().toJSONString();
	}

	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	public static class BuildAreaInstance {

		// These 6 properties are used for JSON serialization.
		private final int xFrom;
		private final int yFrom;
		private final int zFrom;
		private final int xTo;
		private final int yTo;
		private final int zTo;

		public final transient String name;
		public final transient BlockPos spawnPos;
		public final transient BoundingBox box;
		public final transient BlockPos from;
		public final transient BlockPos to;
		public final transient ChunkPos sectionFrom;
		public final transient ChunkPos sectionTo;
		private final transient BoundingBox sectionBox;

		private BuildAreaInstance(String name, BlockPos from, BlockPos to, BlockPos spawnPos) {
			this.name = name;
			box = BoundingBox.fromCorners(from, to);
			this.from = new BlockPos(box.minX(), box.minY(), box.minZ());
			this.to = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
			this.spawnPos = isOutsideBuildArea(spawnPos.getX(), spawnPos.getZ()) ? this.from : spawnPos;
			sectionFrom = new ChunkPos(this.from);
			sectionTo = new ChunkPos(this.to);
			sectionBox = BoundingBox.fromCorners(
				new BlockPos(sectionFrom.x, 0, sectionFrom.z),
				new BlockPos(sectionTo.x, 0, sectionTo.z)
			);
			xFrom = this.from.getX();
			yFrom = this.from.getY();
			zFrom = this.from.getZ();
			xTo = this.to.getX();
			yTo = this.to.getY();
			zTo = this.to.getZ();
		}

		public boolean isOutsideBuildArea(int x, int z) {
			return x < from.getX() || x > to.getX() || z < from.getZ() || z > to.getZ();
		}

		private boolean isInsideBuildArea(BoundingBox otherBox) {
			return box.maxX() >= otherBox.maxX() && box.minX() <= otherBox.minX() && box.maxZ() >= otherBox.maxZ() && box.minZ() <= otherBox.minZ();
		}

		private BoundingBox clampBox(BoundingBox otherBox) {
			if (!box.intersects(otherBox)) {
				throw new HttpException("Requested area is outside of build area", 403);
			}

			return BoundingBox.fromCorners(
				new BlockPos(
					Math.min(Math.max(box.minX(), otherBox.minX()), box.maxX()),
					Math.min(Math.max(box.minY(), otherBox.minY()), box.maxY()),
					Math.min(Math.max(box.minZ(), otherBox.minZ()), box.maxZ())
				),
				new BlockPos(
					Math.max(Math.min(box.maxX(), otherBox.maxX()), box.minX()),
					Math.max(Math.min(box.maxY(), otherBox.maxY()), box.minY()),
					Math.max(Math.min(box.maxZ(), otherBox.maxZ()), box.minZ())
				)
			);
		}

		private BoundingBox clampSectionBox(BoundingBox otherBox) {
			if (!sectionBox.intersects(otherBox)) {
				throw new HttpException("Requested area is outside of build area", 403);
			}
			return BoundingBox.fromCorners(
				new BlockPos(
					Math.min(Math.max(sectionBox.minX(), otherBox.minX()), sectionBox.maxX()),
					0,
					Math.min(Math.max(sectionBox.minZ(), otherBox.minZ()), sectionBox.maxZ())
				),
				new BlockPos(
					Math.max(Math.min(sectionBox.maxX(), otherBox.maxX()), sectionBox.minX()),
					0,
					Math.max(Math.min(sectionBox.maxZ(), otherBox.maxZ()), sectionBox.minZ())
				)
			);
		}

		public boolean equals(BuildAreaInstance other) {
			if (other == null) {
				return false;
			}
			return this.name.equals(other.name) && this.box.equals(other.box);
		}

		public String toJSONString() {
			return new Gson().toJson(this);
		}
	}
}

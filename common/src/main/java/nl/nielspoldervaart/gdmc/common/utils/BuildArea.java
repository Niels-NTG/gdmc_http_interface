package nl.nielspoldervaart.gdmc.common.utils;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nl.nielspoldervaart.gdmc.common.handlers.HandlerBase.HttpException;

public class BuildArea {

	private static BuildAreaInstance buildAreaInstance;

	public static BuildAreaInstance getBuildArea() {
		if (buildAreaInstance == null) {
			throw new HttpException("No build area is specified. Use the /setbuildarea command inside Minecraft to set a build area.", 404);
		}
		return buildAreaInstance;
	}

	public static BuildAreaInstance setBuildArea(BlockPos from, BlockPos to) {
		buildAreaInstance = new BuildAreaInstance(from, to);
		return buildAreaInstance;
	}

	public static void unsetBuildArea() {
		buildAreaInstance = null;
	}

	public static boolean isOutsideBuildArea(BlockPos blockPos, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().isOutsideBuildArea(blockPos.getX(), blockPos.getZ());
		}
		return false;
	}

	public static boolean isOutsideBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return !getBuildArea().isInsideBuildArea(box);
		}
		return false;
	}

	public static BoundingBox clampToBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().clampBox(box);
		}
		return box;
	}

	public static BoundingBox clampChunksToBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().clampSectionBox(box);
		}
		return box;
	}

	public static String toJSONString() {
		return new Gson().toJson(getBuildArea());
	}

	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	public static class BuildAreaInstance {

		// These 6 properties are used for JSON serialisation.
		private final int xFrom;
		private final int yFrom;
		private final int zFrom;
		private final int xTo;
		private final int yTo;
		private final int zTo;

		public final transient BoundingBox box;
		public final transient BlockPos from;
		public final transient BlockPos to;
		public final transient ChunkPos sectionFrom;
		public final transient ChunkPos sectionTo;
		private final transient BoundingBox sectionBox;


		private BuildAreaInstance(BlockPos from, BlockPos to) {
			box = BoundingBox.fromCorners(from, to);
			this.from = new BlockPos(box.minX(), box.minY(), box.minZ());
			this.to = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
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
	}
}

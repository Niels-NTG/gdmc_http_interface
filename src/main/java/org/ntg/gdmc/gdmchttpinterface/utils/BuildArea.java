package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.ntg.gdmc.gdmchttpinterface.handlers.HandlerBase;

public class BuildArea {

	private static BuildAreaInstance buildAreaInstance;

	public static BuildAreaInstance getBuildArea() {
		return getBuildArea(true);
	}
	public static BuildAreaInstance getBuildArea(boolean withinBuildArea) {
		if (withinBuildArea && buildAreaInstance == null) {
			throw new HandlerBase.HttpException("No build area is specified. Use the setbuildarea command inside Minecraft to set a build area.", 404);
		}
		return buildAreaInstance;
	}

	public static void setBuildArea(BlockPos from, BlockPos to) {
		buildAreaInstance = new BuildAreaInstance(from, to);
	}

	public static void unsetBuildArea() {
		buildAreaInstance = null;
	}

	public static boolean isOutsideBuildArea(BlockPos blockPos, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().isOutsideBuildArea(blockPos);
		}
		return false;
	}

	public static boolean isOutsideBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().isOutsideBuildArea(box);
		}
		return false;
	}

	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	public static class BuildAreaInstance {

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


		public BuildAreaInstance(BlockPos from, BlockPos to) {
			box = BoundingBox.fromCorners(from, to);
			this.from = new BlockPos(box.minX(), box.minY(), box.minZ());
			this.to = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
			sectionFrom = new ChunkPos(this.from);
			sectionTo = new ChunkPos(this.to);
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

		public boolean isOutsideBuildArea(BlockPos pos) {
			return isOutsideBuildArea(pos.getX(), pos.getZ());
		}

		public boolean isOutsideBuildArea(BoundingBox otherBox) {
			return box.maxX() < otherBox.minX() || box.minX() > otherBox.maxX() || box.maxZ() < otherBox.minZ() || box.minZ() > otherBox.maxZ();
		}

		public BlockPos clampMinPosition(BlockPos pos) {
			if (from.compareTo(pos) > 0) {
				return from;
			}
			return pos;
		}

		public BlockPos clampMaxPosition(BlockPos pos) {
			if (to.compareTo(pos) < 0) {
				return to;
			}
			return pos;
		}
	}
}

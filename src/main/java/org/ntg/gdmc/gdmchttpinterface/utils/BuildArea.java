package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.ntg.gdmc.gdmchttpinterface.handlers.HandlerBase;

public class BuildArea {

	private static BuildAreaInstance buildAreaInstance;

	public static BuildAreaInstance getBuildArea() {
		if (buildAreaInstance == null) {
			throw new HandlerBase.HttpException("No build area is specified. Use the setbuildarea command inside Minecraft to set a build area.", 404);
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
			return getBuildArea().isOutsideBuildArea(box);
		}
		return false;
	}

	public static BoundingBox clampToBuildArea(BoundingBox box, boolean withinBuildArea) {
		if (withinBuildArea) {
			return getBuildArea().clampBox(box);
		}
		return box;
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


		private BuildAreaInstance(BlockPos from, BlockPos to) {
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

		private boolean isOutsideBuildArea(BoundingBox otherBox) {
			return box.maxX() < otherBox.minX() || box.minX() > otherBox.maxX() || box.maxZ() < otherBox.minZ() || box.minZ() > otherBox.maxZ();
		}

		private BoundingBox clampBox(BoundingBox otherBox) {
			if (!box.intersects(otherBox)) {
				return null;
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

		public int getChunkSpanX() {
			return Math.max(sectionTo.x - sectionFrom.x, 0) + 1;
		}

		public int getChunkSpanZ() {
			return Math.max(sectionTo.z - sectionFrom.z, 0) + 1;
		}
	}
}

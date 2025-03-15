package nl.nielspoldervaart.gdmc.common.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CustomHeightmap {

	private final BitStorage data;
	private final Predicate<BlockState> isOpaque;
	private final ChunkAccess chunk;
	private final Optional<Integer> yMinBound;
	private final Optional<Integer> yMaxBound;

	private CustomHeightmap(ChunkAccess chunk, Types heightmapType, Optional<Integer> yMinBound, Optional<Integer> yMaxBound) {
		this.isOpaque = heightmapType.isOpaque();
		this.chunk = chunk;
		int i = Mth.ceillog2(chunk.getHeight() + 1);
		this.data = new SimpleBitStorage(i, 256);
		this.yMinBound = yMinBound;
		this.yMaxBound = yMaxBound;
	}

	private CustomHeightmap(ChunkAccess chunk, Predicate<BlockState> isOpaque, Optional<Integer> yMinBound, Optional<Integer> yMaxBound) {
		this.isOpaque = isOpaque;
		this.chunk = chunk;
		int i = Mth.ceillog2(chunk.getHeight() + 1);
		this.data = new SimpleBitStorage(i, 256);
		this.yMinBound = yMinBound;
		this.yMaxBound = yMaxBound;
	}

	public static CustomHeightmap primeHeightmaps(ChunkAccess chunk, ArrayList<BlockState> blockList, ArrayList<String> blockTagLocationKeyList, Optional<Integer> yMinBound, Optional<Integer> yMaxBound) {
		Predicate<BlockState> isOpaque = blockState -> !blockList.contains(blockState) && !hasBlockTagKey(blockState, blockTagLocationKeyList);
		CustomHeightmap customHeightmap = new CustomHeightmap(chunk, isOpaque, yMinBound, yMaxBound);
		return primeHeightmaps(chunk, customHeightmap);
	}

	public static CustomHeightmap primeHeightmaps(ChunkAccess chunk, CustomHeightmap.Types heightmapType, Optional<Integer> yMinBound, Optional<Integer> yMaxBound) {
		CustomHeightmap customHeightmap = new CustomHeightmap(chunk, heightmapType, yMinBound, yMaxBound);
		return primeHeightmaps(chunk, customHeightmap);
	}

	private static CustomHeightmap primeHeightmaps(ChunkAccess chunk, CustomHeightmap customHeightmap) {
		int yMin = Math.clamp(
			customHeightmap.yMinBound.orElse(getChunkMinY(chunk)),
			getChunkMinY(chunk),
			getChunkMaxY(chunk)
		);
		int yMax = Math.clamp(
			customHeightmap.yMaxBound.orElse(getChunkMaxY(chunk)),
			getChunkMinY(chunk),
			getChunkMaxY(chunk)
		);
		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = yMax - 1; y >= yMin; y--) {
					blockPos.set(x, y, z);
					BlockState blockstate = chunk.getBlockState(blockPos);
					customHeightmap.setHeight(x, z, y + 1);
					if (customHeightmap.isOpaque.test(blockstate)) {
						break;
					}
				}
			}
		}
		return customHeightmap;
	}

	private void setHeight(int x, int z, int y) {
		this.data.set(getIndex(x, z), y - getChunkMinY(this.chunk));
	}

	public int getFirstAvailable(int x, int z) {
		return this.getFirstAvailable(getIndex(x, z));
	}

	private int getFirstAvailable(int index) {
		return this.data.get(index) + getChunkMinY(this.chunk);
	}

	private static int getIndex(int x, int z) {
		return x + z * 16;
	}

	/**
	 * Get min Y value of vertical world limit at given chunk
	 *
	 * @param chunk     chunk to check minimum height of
	 * @return          min Y value
	 */
	private static int getChunkMinY(ChunkAccess chunk) {
		#if (MC_VER == MC_1_21_4)
		return chunk.getMinY();
		#else
		return chunk.getMinBuildHeight();
		#endif
	}

	/**
	 * Get max Y value of vertical world limit at given chunk
	 *
	 * @param chunk     chunk to check maximum height of
	 * @return          max Y value
	 */
	private static int getChunkMaxY(ChunkAccess chunk) {
		#if (MC_VER == MC_1_21_4)
		return chunk.getMaxY();
		#else
		return chunk.getMaxBuildHeight();
		#endif
	}

	private static boolean hasBlockTagKey(BlockState blockState, ArrayList<String> inputBlockTagLocationKeyList) {
		if (inputBlockTagLocationKeyList.isEmpty()) {
			return false;
		}
		return inputBlockTagLocationKeyList.stream().anyMatch(inputBlockTagLocationKey -> {
			Stream<TagKey<Fluid>> fluidStateTagKeys = blockState.getFluidState().getTags();
			Stream<TagKey<Block>> blockTagKeys = blockState.getTags();
			if (fluidStateTagKeys.anyMatch(fluidTagKey -> isSameTagLocationName(fluidTagKey, inputBlockTagLocationKey))) {
				return true;
			}
			return blockTagKeys.anyMatch(blockTagKey -> isSameTagLocationName(blockTagKey, inputBlockTagLocationKey));
		});
	}

	private static boolean isSameTagLocationName(TagKey<?> tagKey, String inputTagLocationKey) {
		return tagKey.location().toString().equals(inputTagLocationKey);
	}

	private static final Predicate<BlockState> NO_PLANTS = blockState ->
		!blockState.is(BlockTags.LEAVES) && !blockState.is(BlockTags.LOGS) && !blockState.is(Blocks.BEE_NEST) && !blockState.is(Blocks.MANGROVE_ROOTS) && !blockState.is(Blocks.MUDDY_MANGROVE_ROOTS) &&
		!blockState.is(Blocks.BROWN_MUSHROOM_BLOCK) && !blockState.is(Blocks.RED_MUSHROOM_BLOCK) && !blockState.is(Blocks.MUSHROOM_STEM) &&
		!blockState.is(Blocks.PUMPKIN) &&
		!blockState.is(Blocks.MELON) &&
		!blockState.is(Blocks.MOSS_BLOCK) && !blockState.is(Blocks.NETHER_WART_BLOCK) &&
		!blockState.is(Blocks.CACTUS) &&
		!blockState.is(Blocks.FARMLAND) &&
		!blockState.is(BlockTags.CORAL_BLOCKS) && !blockState.is(Blocks.SPONGE) && !blockState.is(Blocks.WET_SPONGE) &&
		!blockState.is(Blocks.BAMBOO) &&
		!blockState.is(Blocks.COBWEB) &&
		!blockState.is(Blocks.SCULK)
		;

	public enum Types implements StringRepresentable {
		@SuppressWarnings("unused") MOTION_BLOCKING_NO_PLANTS(
			"MOTION_BLOCKING_NO_PLANTS",
			(blockState) ->
				(
					!blockState.is(Blocks.AIR) &&
					#if (MC_VER == MC_1_19_2)
					blockState.getMaterial().blocksMotion()
					#else
					blockState.blocksMotion()
					#endif
					|| !blockState.getFluidState().isEmpty()
				) && NO_PLANTS.test(blockState)
		),
		@SuppressWarnings("unused") OCEAN_FLOOR_NO_PLANTS(
			"OCEAN_FLOOR_NO_PLANTS",
			(blockState) ->
				!blockState.is(Blocks.AIR) &&
				#if (MC_VER == MC_1_19_2)
				blockState.getMaterial().blocksMotion()
				#else
				blockState.blocksMotion()
				#endif
				&& NO_PLANTS.test(blockState)
		);

		private final String serializationKey;
		private final Predicate<BlockState> isOpaque;
		Types(String serializationKey, Predicate<BlockState> predicate) {
			this.serializationKey = serializationKey;
			this.isOpaque = predicate;
		}

		public Predicate<BlockState> isOpaque() {
			return this.isOpaque;
		}

		@SuppressWarnings("NullableProblems")
		@Override
		public String getSerializedName() {
			return this.serializationKey;
		}
	}
}

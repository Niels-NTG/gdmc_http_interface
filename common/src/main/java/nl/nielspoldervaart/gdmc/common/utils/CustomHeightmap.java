package nl.nielspoldervaart.gdmc.common.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.function.Predicate;

public class CustomHeightmap {

	private final BitStorage data;
	private final Predicate<BlockState> isOpaque;
	private final ChunkAccess chunk;

	public CustomHeightmap(ChunkAccess chunk, Types heightmapType) {
		this.isOpaque = heightmapType.isOpaque();
		this.chunk = chunk;
		int i = Mth.ceillog2(chunk.getHeight() + 1);
		this.data = new SimpleBitStorage(i, 256);
	}

	public CustomHeightmap(ChunkAccess chunk, Predicate<BlockState> isOpaque) {
		this.isOpaque = isOpaque;
		this.chunk = chunk;
		int i = Mth.ceillog2(chunk.getHeight() + 1);
		this.data = new SimpleBitStorage(i, 256);
	}

	public static CustomHeightmap primeHeightmaps(ChunkAccess chunk, CustomHeightmap.Types heightmapType) {
		CustomHeightmap customHeightmap = new CustomHeightmap(chunk, heightmapType);
		return primeHeightmaps(chunk, customHeightmap);
	}

	private static CustomHeightmap primeHeightmaps(ChunkAccess chunk, CustomHeightmap customHeightmap) {
		return primeHeightmaps(chunk, customHeightmap, Integer.MAX_VALUE);
	}

	private static CustomHeightmap primeHeightmaps(ChunkAccess chunk, CustomHeightmap customHeightmap, int fromY) {
		int j = fromY == Integer.MAX_VALUE ? chunk.getMaxBuildHeight() : fromY;
		BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
		for(int k = 0; k < 16; ++k) {
			for(int l = 0; l < 16; ++l) {
				for(int i1 = j - 1; i1 >= chunk.getMinBuildHeight(); --i1) {
					blockpos$mutableblockpos.set(k, i1, l);
					BlockState blockstate = chunk.getBlockState(blockpos$mutableblockpos);
					if (customHeightmap.isOpaque.test(blockstate)) {
						customHeightmap.setHeight(k, l, i1 + 1);
						break;
					}
				}
			}
		}
		return customHeightmap;
	}

	public static CustomHeightmap primeHeightmaps(ChunkAccess chunk, ArrayList<BlockState> blockList, int fromY) {
		Predicate<BlockState> isOpaque = blockState -> !blockList.contains(blockState);
		CustomHeightmap customHeightmap = new CustomHeightmap(chunk, isOpaque);
		return primeHeightmaps(chunk, customHeightmap, fromY);
	}

	private void setHeight(int x, int z, int y) {
		this.data.set(getIndex(x, z), y - this.chunk.getMinBuildHeight());
	}

	public int getFirstAvailable(int x, int z) {
		return this.getFirstAvailable(getIndex(x, z));
	}

	private int getFirstAvailable(int index) {
		return this.data.get(index) + this.chunk.getMinBuildHeight();
	}

	private static int getIndex(int x, int z) {
		return x + z * 16;
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

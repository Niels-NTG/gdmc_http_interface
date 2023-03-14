package com.gdmc.httpinterfacemod.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.*;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.state.properties.Property;

public class ReplaceCommand {
    static final BlockInput HOLLOW_CORE = new BlockInput(Blocks.AIR.defaultBlockState(), Collections.emptySet(), (CompoundTag)null);
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> p_214443_, CommandBuildContext p_214444_) {
        p_214443_.register(Commands.literal("replace").requires((p_137384_) -> {
            return p_137384_.hasPermission(2);
        }).then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).then(Commands.argument("block", BlockStateArgument.block(p_214444_)).executes((p_137405_) -> {
            return fillBlocks(p_137405_.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(p_137405_, "from"), BlockPosArgument.getLoadedBlockPos(p_137405_, "to")));
        })))));
    }
    public static boolean isBlockReplaceable(Block[] replaceableBlocks, Block target) {

        for (Block replaceableBlock : replaceableBlocks) {
            if (replaceableBlock.equals(target)) {
                return true;
            }
        }
        return false;
    }

    static Block[] replaceableBlocks = {Blocks.OAK_LOG, Blocks.BIRCH_LOG};

    private static int fillBlocks(CommandSourceStack p_137386_, BoundingBox p_137387_) throws CommandSyntaxException {


        var air = new BlockInput(Blocks.AIR.defaultBlockState(), new HashSet<Property<?>>(), null);

        List<BlockPos> list = Lists.newArrayList();
        ServerLevel serverlevel = p_137386_.getLevel();
        int j = 0;

        for(BlockPos blockpos : BlockPos.betweenClosed(p_137387_.minX(), p_137387_.minY(), p_137387_.minZ(), p_137387_.maxX(), p_137387_.maxY(), p_137387_.maxZ())) {
            var block = new BlockInWorld(serverlevel, blockpos, true);
            var replaceable = isBlockReplaceable(replaceableBlocks, block.getState().getBlock());
            if (replaceable) {
                BlockInput blockinput = Mode.REPLACE.filter.filter(p_137387_, blockpos, air, serverlevel);
                if (blockinput != null) {
                    BlockEntity blockentity = serverlevel.getBlockEntity(blockpos);
                    Clearable.tryClear(blockentity);
                    if (blockinput.place(serverlevel, blockpos, 2)) {
                        list.add(blockpos.immutable());
                        ++j;
                    }
                }
            }
        }

        for(BlockPos blockpos1 : list) {
            Block block = serverlevel.getBlockState(blockpos1).getBlock();
            serverlevel.blockUpdated(blockpos1, block);
        }

        if (j == 0) {
            throw ERROR_FAILED.create();
        } else {
            p_137386_.sendSuccess(Component.translatable("commands.fill.success", j), true);
            return j;
        }
    }

    static enum Mode {
        REPLACE((p_137433_, p_137434_, p_137435_, p_137436_) -> {
            return p_137435_;
        }),
        OUTLINE((p_137428_, p_137429_, p_137430_, p_137431_) -> {
            return p_137429_.getX() != p_137428_.minX() && p_137429_.getX() != p_137428_.maxX() && p_137429_.getY() != p_137428_.minY() && p_137429_.getY() != p_137428_.maxY() && p_137429_.getZ() != p_137428_.minZ() && p_137429_.getZ() != p_137428_.maxZ() ? null : p_137430_;
        }),
        HOLLOW((p_137423_, p_137424_, p_137425_, p_137426_) -> {
            return p_137424_.getX() != p_137423_.minX() && p_137424_.getX() != p_137423_.maxX() && p_137424_.getY() != p_137423_.minY() && p_137424_.getY() != p_137423_.maxY() && p_137424_.getZ() != p_137423_.minZ() && p_137424_.getZ() != p_137423_.maxZ() ? HOLLOW_CORE : p_137425_;
        }),
        DESTROY((p_137418_, p_137419_, p_137420_, p_137421_) -> {
            p_137421_.destroyBlock(p_137419_, true);
            return p_137420_;
        });

        public final SetBlockCommand.Filter filter;

        private Mode(SetBlockCommand.Filter p_137416_) {
            this.filter = p_137416_;
        }
    }
}
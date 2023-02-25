package com.gdmc.httpinterfacemod.commands;

import com.gdmc.httpinterfacemod.handlers.BuildAreaHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SetBuildAreaCommand {

    private static final String COMMAND_NAME = "setbuildarea";
    private static final Logger LOGGER = LogManager.getLogger();

    private SetBuildAreaCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
            .then(
                Commands.argument("from", BlockPosArgument.blockPos())
            .then(
                Commands.argument("to", BlockPosArgument.blockPos())
            .executes( context -> perform(context, BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to"))))
            )
        );
        // TODO remove requirement of the position to be loaded
    }

    private static int perform(CommandContext<CommandSourceStack> commandSourceContext, BlockPos from, BlockPos to) {
        int x1 = from.getX();
        int y1 = from.getY();
        int z1 = from.getZ();
        int x2 = to.getX();
        int y2 = to.getY();
        int z2 = to.getZ();

        BuildAreaHandler.setBuildArea(x1, y1, z1, x2, y2, z2);
        String feedback = String.format("Build area set to %d, %d, %d to %d, %d, %d,", x1, y1, z1, x2, y2, z2 );
        commandSourceContext.getSource().sendSuccess(Component.nullToEmpty(feedback), true);
        LOGGER.info(feedback);
        return 1;
    }
}

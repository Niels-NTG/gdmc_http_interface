package org.ntg.gdmc.gdmchttpinterface.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.ntg.gdmc.gdmchttpinterface.utils.BuildArea;

public final class SetBuildAreaCommand {

    private static final String COMMAND_NAME = "setbuildarea";

    private SetBuildAreaCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
            .executes(SetBuildAreaCommand :: unsetBuildArea)
            .then(
                Commands.argument("from", BlockPosArgument.blockPos())
            .then(
                Commands.argument("to", BlockPosArgument.blockPos())
            .executes( context -> setBuildArea(
                context,
                context.getArgument("from", Coordinates.class).getBlockPos(context.getSource()),
                context.getArgument("to", Coordinates.class).getBlockPos(context.getSource())
        )))));
    }

    private static int unsetBuildArea(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        BuildArea.unsetBuildArea();
        commandSourceStackCommandContext.getSource().sendSuccess(() -> Component.nullToEmpty("Build area unset"), true);
        return 1;
    }

    private static int setBuildArea(CommandContext<CommandSourceStack> commandSourceContext, BlockPos from, BlockPos to) {
        BuildArea.BuildAreaInstance newBuildArea = BuildArea.setBuildArea(from, to);
        String feedback = String.format("Build area set from %s to %s", newBuildArea.from.toShortString(), newBuildArea.to.toShortString());
        commandSourceContext.getSource().sendSuccess(() -> Component.nullToEmpty(feedback), true);
        return 1;
    }
}

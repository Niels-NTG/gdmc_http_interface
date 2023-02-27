package com.gdmc.httpinterfacemod.commands;

import com.gdmc.httpinterfacemod.config.GdmcHttpConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class SetHttpInterfacePort {

	private static final String COMMAND_NAME = "sethttpport";

	private SetHttpInterfacePort() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(COMMAND_NAME)
			.then(Commands.argument("port", IntegerArgumentType.integer(0, 65535))
			.executes(context -> perform(context, IntegerArgumentType.getInteger(context, "port")))
		));
	}

	private static int perform(CommandContext<CommandSourceStack> commandSourceContext, int newPortNumber) {
		try {
			GdmcHttpConfig.HTTP_INTERFACE_PORT.set(newPortNumber);
			commandSourceContext.getSource().sendSuccess(Component.nullToEmpty(
				String.format("Port changed to %s. Reload the world for it to take effect.", newPortNumber)
			), true);
		} catch (IllegalArgumentException e) {
			commandSourceContext.getSource().sendFailure(Component.nullToEmpty(
				String.format("Cannot change port number to %s: %s", newPortNumber, e.getMessage())
			));
		}
		return newPortNumber;
	}
}

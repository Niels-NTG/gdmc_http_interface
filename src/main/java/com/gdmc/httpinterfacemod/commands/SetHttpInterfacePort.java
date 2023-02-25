package com.gdmc.httpinterfacemod.commands;

import com.gdmc.httpinterfacemod.GdmcHttpServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public final class SetHttpInterfacePort {

	private static final String COMMAND_NAME = "sethttpport";

	private static final Logger LOGGER = LogManager.getLogger();

	private SetHttpInterfacePort() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(COMMAND_NAME)
			.then(Commands.argument("port", IntegerArgumentType.integer(0, 65535))
			.executes(context -> perform(context, IntegerArgumentType.getInteger(context, "port")))
		));
	}

	private static int perform(CommandContext<CommandSourceStack> commandSourceContext, int newPortNumber) {
		LOGGER.info(newPortNumber);

		try {
			GdmcHttpServer.startServer(newPortNumber);
			commandSourceContext.getSource().sendSuccess(Component.nullToEmpty(
				String.format("Port changed to %s", newPortNumber)
			), true);
		} catch (IOException e) {
			LOGGER.error(e.getStackTrace());
			commandSourceContext.getSource().sendFailure(Component.nullToEmpty(
				String.format("Cannot change port number to %s: %s", newPortNumber, e.getMessage())
			));
			throw new RuntimeException(e);
		}

		return 1;
	}
}

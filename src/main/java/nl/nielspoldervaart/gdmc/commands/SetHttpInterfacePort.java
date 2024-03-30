package nl.nielspoldervaart.gdmc.commands;

import nl.nielspoldervaart.gdmc.config.GdmcHttpConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import nl.nielspoldervaart.gdmc.utils.Feedback;

public final class SetHttpInterfacePort {

	private static final String COMMAND_NAME = "sethttpport";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(COMMAND_NAME)
			.executes(SetHttpInterfacePort::unsetInterfacePort)
			.then(Commands.argument("port", IntegerArgumentType.integer(0, 65535))
			.executes(context -> setInterfacePort(context, IntegerArgumentType.getInteger(context, "port")))
		));
	}

	private static int unsetInterfacePort(CommandContext<CommandSourceStack> commandSourceStack) {
		int defaultPort = GdmcHttpConfig.HTTP_INTERFACE_PORT.getDefault();
		GdmcHttpConfig.HTTP_INTERFACE_PORT.set(defaultPort);
		Feedback.sendSucces(
			commandSourceStack,
			Feedback.chatMessage("Port changed back to default value of ").append(Feedback.copyOnClickText(String.valueOf(defaultPort))).append(". Reload the world for it to take effect.")
		);
		return defaultPort;
	}

	private static int setInterfacePort(CommandContext<CommandSourceStack> commandSourceContext, int newPortNumber) {
		try {
			GdmcHttpConfig.HTTP_INTERFACE_PORT.set(newPortNumber);
			Feedback.sendSucces(
				commandSourceContext,
				Feedback.chatMessage("Port changed to ").append(Feedback.copyOnClickText(String.valueOf(newPortNumber))).append(". Reload the world for it to take effect.")
			);
		} catch (IllegalArgumentException e) {
			commandSourceContext.getSource().sendFailure(
				Feedback.chatMessage(String.format("Cannot change port number to %s: %s", newPortNumber, e.getMessage()))
			);
		}
		return newPortNumber;
	}
}

package nl.nielspoldervaart.gdmc.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;
import nl.nielspoldervaart.gdmc.fabric.config.GdmcHttpConfig;

public final class SetHttpInterfacePort {
	static final String COMMAND_NAME = "sethttpport";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(SetHttpInterfacePort.COMMAND_NAME)
			.executes(SetHttpInterfacePort :: unsetInterfacePort)
			.then(Commands.argument("port", IntegerArgumentType.integer(0, 65535))
				.executes(context -> setInterfacePort(context, IntegerArgumentType.getInteger(context, "port")))
			));
	}

	public static int unsetInterfacePort(CommandContext<CommandSourceStack> commandSourceStack) {
		int defaultPort = GdmcHttpConfig.DEFAULT_HTTP_INTERFACE_PORT;
		GdmcHttpConfig.HTTP_INTERFACE_PORT = defaultPort;
		Feedback.sendSucces(
			commandSourceStack,
			Feedback.chatMessage("Port changed back to default value of ").append(Feedback.copyOnClickText(String.valueOf(defaultPort))).append(". Reload the world for it to take effect.")
		);
		return defaultPort;
	}

	public static int setInterfacePort(CommandContext<CommandSourceStack> commandSourceContext, int newPortNumber) {
		try {
			GdmcHttpConfig.HTTP_INTERFACE_PORT = newPortNumber;
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

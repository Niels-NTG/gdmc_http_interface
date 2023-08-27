package org.ntg.gdmc.gdmchttpinterface.commands;

import org.ntg.gdmc.gdmchttpinterface.config.GdmcHttpConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.ntg.gdmc.gdmchttpinterface.utils.Feedback;

public final class SetHttpInterfacePort {

	private static final String COMMAND_NAME = "sethttpport";

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
			commandSourceContext.getSource().sendSuccess(() ->
				Feedback.chatMessage("Port changed to ").append(Feedback.copyOnClickText(String.valueOf(newPortNumber))).append(". Reload the world for it to take effect."),
				true
			);
		} catch (IllegalArgumentException e) {
			commandSourceContext.getSource().sendFailure(
				Feedback.chatMessage(String.format("Cannot change port number to %s: %s", newPortNumber, e.getMessage()))
			);
		}
		return newPortNumber;
	}
}

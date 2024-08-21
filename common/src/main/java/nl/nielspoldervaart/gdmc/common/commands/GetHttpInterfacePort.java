package nl.nielspoldervaart.gdmc.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import nl.nielspoldervaart.gdmc.common.GdmcHttpServer;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;

public final class GetHttpInterfacePort {

	static final String COMMAND_NAME = "gethttpport";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(COMMAND_NAME)
			.executes(GetHttpInterfacePort :: perform)
		);
	}

	private static int perform(CommandContext<CommandSourceStack> commandSourceContext) {
		int currentPort = GdmcHttpServer.getCurrentHttpPort();
		Feedback.sendSuccess(
			commandSourceContext,
			Feedback.chatMessage("Current GDMC-HTTP port: ").append(Feedback.copyOnClickText(String.valueOf(currentPort)))
		);
		return currentPort;
	}
}

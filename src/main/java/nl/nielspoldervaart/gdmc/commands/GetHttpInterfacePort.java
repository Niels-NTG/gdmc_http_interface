package nl.nielspoldervaart.gdmc.commands;

import nl.nielspoldervaart.gdmc.GdmcHttpServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import nl.nielspoldervaart.gdmc.utils.Feedback;

public class GetHttpInterfacePort {

	private static final String COMMAND_NAME = "gethttpport";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(COMMAND_NAME)
			.executes(GetHttpInterfacePort :: perform)
		);
	}

	private static int perform(CommandContext<CommandSourceStack> commandSourceContext) {
		int currentPort = GdmcHttpServer.getCurrentHttpPort();
		Feedback.sendSucces(
			commandSourceContext,
			Feedback.chatMessage("Current GDMC-HTTP port: ").append(Feedback.copyOnClickText(String.valueOf(currentPort)))
		);
		return currentPort;
	}
}

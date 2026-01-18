package nl.nielspoldervaart.gdmc.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import nl.nielspoldervaart.gdmc.common.handlers.HandlerBase;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea.BuildAreaInstance;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;

import java.util.HashMap;
import java.util.Map;

public final class BuildAreaCommand {

	static final String COMMAND_NAME = "buildarea";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(COMMAND_NAME)
			.executes(BuildAreaCommand::getCurrentBuildArea)
			.then(
				Commands.literal("remove")
					.executes(BuildAreaCommand::unsetBuildArea)
					.then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(context -> unsetBuildArea(
						context,
						context.getArgument("name", String.class)
					)))
			)
			.then(
				Commands.literal("set")
//                    TODO suggest current location as auto-complete suggestion?
					.then(Commands.argument("from", BlockPosArgument.blockPos())
					.then(Commands.argument("to", BlockPosArgument.blockPos())
					.executes( context -> setBuildArea(
						context,
						context.getArgument("from", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("to", Coordinates.class).getBlockPos(context.getSource()),
						"default"
					))
					.then(Commands.argument("name",  StringArgumentType.greedyString())
					.executes(context -> setBuildArea(
						context,
						context.getArgument("from", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("to", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("name", String.class)
					)))))
			)
			.then(
				Commands.literal("load")
					.then(Commands.argument("name", StringArgumentType.greedyString())
					// TODO autocomplete with existing saved names
					.executes(context -> loadBuildArea(
						context,
						context.getArgument("name", String.class)
					)))
			)
			.then(
				Commands.literal("list")
					.executes(BuildAreaCommand::listBuildAreas)
			)
			.then(
				Commands.literal("tp")
					.executes(BuildAreaCommand::gotoBuildArea)
					.then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(context -> gotoBuildArea(
						context,
						context.getArgument("name", String.class)
					)))
			)
		);
		// TODO implement command to generate TP book
	}

	private static int getCurrentBuildArea(CommandContext<CommandSourceStack> context) {
		try {
			BuildAreaInstance buildArea = BuildArea.getCurrentBuildArea();
			Feedback.sendSuccess(
				context,
				Feedback.chatMessage("Current build area: ").append(buildAreaCopyText(buildArea))
			);
			return 1;
		} catch (HandlerBase.HttpException exception) {
			Feedback.sendFailure(context, Feedback.chatMessage("No build area set"));
			return 0;
		}
	}

	private static int unsetBuildArea(CommandContext<CommandSourceStack> context) {
		try {
			BuildAreaInstance buildArea = BuildArea.getCurrentBuildArea();
			return unsetBuildArea(context, buildArea.name);
		} catch (HandlerBase.HttpException ignored) {
			Feedback.sendFailure(context, Feedback.chatMessage("No build area set"));
			return 0;
		}
	}

	private static int unsetBuildArea(CommandContext<CommandSourceStack> context, String name) {
		boolean result = BuildArea.unsetBuildArea(context.getSource().getServer(), name);
		if (result) {
//            TODO show unset area name
			Feedback.sendSuccess(
				context,
				Feedback.chatMessage("Removed build area ").append(buildAreaNameText(name))
			);
			return 1;
		}
		sendCannotFindBuildArea(context, name);
		return 0;
	}

	private static int setBuildArea(CommandContext<CommandSourceStack> context, BlockPos from, BlockPos to, String name) {
		BlockPos entityPosition = context.getSource().getEntity() == null ?
			from :
			context.getSource().getEntity().blockPosition();
		BuildAreaInstance buildArea = BuildArea.setCurrentBuildArea(
			from, to,
			context.getSource().getServer(),
			entityPosition,
			name
		);
		Feedback.sendSuccess(
			context,
			Feedback.chatMessage("Current build area: ").append(buildAreaCopyText(buildArea))
		);
		return 1;
	}

	private static int loadBuildArea(CommandContext<CommandSourceStack> context, String name) {
		BuildAreaInstance buildArea = BuildArea.setCurrentBuildAreaFromStorage(context.getSource().getServer(), name);
		if (buildArea == null) {
			sendCannotFindBuildArea(context, name);
			return 0;
		}
		Feedback.sendSuccess(
			context,
			Feedback.chatMessage("Build area ")
				.append(buildAreaCopyText(buildArea))
				.append(" loaded")
		);
		return 1;
	}

	private static int listBuildAreas(CommandContext<CommandSourceStack> context) {
		HashMap<String, BuildAreaInstance> savedBuildAreas = BuildArea.getSavedBuildAreas(context.getSource().getServer());
		if (savedBuildAreas.isEmpty()) {
			Feedback.sendSuccess(
				context,
				Feedback.chatMessage("No saved build areas")
			);
			return 0;
		}

		BuildAreaInstance currentBuildArea = null;
		try {
			currentBuildArea = BuildArea.getCurrentBuildArea();
		} catch (HandlerBase.HttpException ignored) {}
		MutableComponent chatMessage = Feedback.chatMessage("Saved build areas:\n");
		for (Map.Entry<String, BuildAreaInstance> entry : savedBuildAreas.entrySet()) {
			String name = entry.getKey();
			BuildAreaInstance buildArea = entry.getValue();
			boolean isCurrentBuildArea = buildArea.equals(currentBuildArea);
			chatMessage.append(
				Component.literal(isCurrentBuildArea ? "☑" : "☐")
					.withStyle((Style style) ->
						style.withClickEvent(
							new ClickEvent.RunCommand(String.format("buildarea load %s", name))
						).withHoverEvent(
							new HoverEvent.ShowText(Component.literal("Click to set as current build area"))
						)
					)
			);
			chatMessage.append(" ");
			chatMessage.append(buildAreaNameText(name));
			chatMessage.append(Feedback.copyOnClickText(
				String.format("from %s to %s", buildArea.from.toShortString(), buildArea.to.toShortString()),
				buildArea.toJSONString()
			));
			chatMessage.append("\n");
		}
		Feedback.sendSuccess(context, chatMessage);
		return 1;
	}

	private static int gotoBuildArea(CommandContext<CommandSourceStack> context) {
		try {
			BuildAreaInstance buildArea = BuildArea.getCurrentBuildArea();
			return gotoBuildArea(context, buildArea.name);
		} catch (HandlerBase.HttpException ignored) {
			Feedback.sendFailure(context, Feedback.chatMessage("No current build area set"));
			return 0;
		}
	}

	private static int gotoBuildArea(CommandContext<CommandSourceStack> context, String name) {
		MinecraftServer server = context.getSource().getServer();
		BuildAreaInstance buildArea = BuildArea.createBuildAreaInstanceFromData(
			server,
			name
		);
		if (buildArea != null) {
			try {
				context.getSource().dispatcher().execute(
					String.format(
						"tp %s %s %s",
						buildArea.spawnPos.getX(),
						buildArea.spawnPos.getY(),
						buildArea.spawnPos.getZ()
					),
					context.getSource()
				);
				return 1;
			} catch (CommandSyntaxException e) {
				Feedback.sendFailure(context, Component.literal(e.getMessage()));
				return 0;
			}
		}
		return 0;
	}

	private static MutableComponent buildAreaNameText(String name) {
		return Component.empty().append(Component.literal(name).withStyle(ChatFormatting.GREEN)).append(" ");
	}

	private static MutableComponent buildAreaCopyText(BuildAreaInstance buildArea) {
		return buildAreaNameText(buildArea.name).append(
			Feedback.copyOnClickText(
				String.format("from %s to %s", buildArea.from.toShortString(), buildArea.to.toShortString()),
				buildArea.toJSONString()
			));
	}

	private static void sendCannotFindBuildArea(CommandContext<CommandSourceStack> context, String name) {
		Feedback.sendFailure(
			context,
			Feedback.chatMessage(String.format("Cannot find build area with name \"%s\"", name))
		);
	}
}

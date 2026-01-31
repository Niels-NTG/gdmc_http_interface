package nl.nielspoldervaart.gdmc.common.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import nl.nielspoldervaart.gdmc.common.handlers.HandlerBase;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea.BuildAreaInstance;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;
import nl.nielspoldervaart.gdmc.common.utils.SavedBuildAreaNameArgument;

import java.util.ArrayList;

public final class BuildAreaCommand {

	static final String COMMAND_NAME = "buildarea";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal(COMMAND_NAME)
				.executes(BuildAreaCommand::getCurrentBuildArea)
			.then(
				Commands.literal("remove")
					.executes(BuildAreaCommand::unsetBuildArea)
					.then(Commands.argument("name", new SavedBuildAreaNameArgument())
					.executes(context -> unsetBuildArea(
						context,
						context.getArgument("name", String.class)
					)))
			)
			.then(
				Commands.literal("set")
					.then(Commands.argument("from", BlockPosArgument.blockPos())
					.then(Commands.argument("to", BlockPosArgument.blockPos())
					.executes( context -> setBuildArea(
						context,
						context.getArgument("from", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("to", Coordinates.class).getBlockPos(context.getSource()),
						"default"
					))
					.then(Commands.argument("name", new SavedBuildAreaNameArgument())
					.executes(context -> setBuildArea(
						context,
						context.getArgument("from", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("to", Coordinates.class).getBlockPos(context.getSource()),
						context.getArgument("name", String.class)
					)))))
			)
			.then(
				Commands.literal("load")
					.then(Commands.argument("name", new SavedBuildAreaNameArgument())
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
					.then(Commands.argument("name", new SavedBuildAreaNameArgument())
					.executes(context -> gotoBuildArea(
						context,
						context.getArgument("name", String.class)
					)))
			).then(
				Commands.literal("makebook")
					.executes(BuildAreaCommand::makeBook)
					.then(Commands.argument("author", StringArgumentType.greedyString())
					.executes(context -> makeBook(
						context,
						context.getArgument("author", String.class)
					)))
			)
		);
	}

	private static int getCurrentBuildArea(CommandContext<CommandSourceStack> context) {
		try {
			BuildAreaInstance buildArea = BuildArea.getCurrentBuildArea();
			sendCurrentBuildArea(context, buildArea);
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
		float entityDirection = context.getSource().getEntity() == null ?
			Direction.EAST.toYRot() :
			context.getSource().getEntity().getYRot();
		BuildAreaInstance buildArea = BuildArea.setCurrentBuildArea(
			from, to,
			entityPosition,
			entityDirection,
			context.getSource().getServer(),
			name
		);
		sendCurrentBuildArea(context, buildArea);
		return 1;
	}

	private static int loadBuildArea(CommandContext<CommandSourceStack> context, String name) {
		BuildAreaInstance buildArea = BuildArea.setCurrentBuildAreaFromStorage(context.getSource().getServer(), name);
		if (buildArea == null) {
			sendCannotFindBuildArea(context, name);
			return 0;
		}
		sendCurrentBuildArea(context, buildArea);
		return 1;
	}

	private static int listBuildAreas(CommandContext<CommandSourceStack> context) {
		ArrayList<BuildAreaInstance> savedBuildAreas = BuildArea.getSavedBuildAreas(context.getSource().getServer());
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
		for (BuildAreaInstance buildArea : savedBuildAreas) {
			boolean isCurrentBuildArea = buildArea.equals(currentBuildArea);
			chatMessage.append(
				Component.literal(isCurrentBuildArea ? "☑" : "☐")
					.withStyle((Style style) ->
						style.withClickEvent(
							new ClickEvent.RunCommand(String.format("buildarea load %s", buildArea.name))
						).withHoverEvent(
							new HoverEvent.ShowText(Component.literal("Click to set as current build area"))
						)
					)
			);
			chatMessage.append(" ");
			chatMessage.append(buildAreaNameText(buildArea.name));
			chatMessage.append(Feedback.copyOnClickText(
				String.format("from %s to %s", buildArea.from.toShortString(), buildArea.to.toShortString()),
				buildArea.toJSONString()
			));
			if (savedBuildAreas.getLast() != buildArea) {
				chatMessage.append("\n");
			}
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
		BuildAreaInstance buildArea = BuildArea.createBuildAreaInstanceFromData(
			context.getSource().getServer(),
			name
		);
		if (buildArea != null) {
			try {
				context.getSource().dispatcher().execute(
					String.format(
						"tp @s %s %s %s %s 0",
						buildArea.spawnPos.getX(),
						buildArea.spawnPos.getY(),
						buildArea.spawnPos.getZ(),
						buildArea.spawnLookRotation
					),
					context.getSource()
				);
				return 1;
			} catch (CommandSyntaxException e) {
				Feedback.sendFailure(context, Component.literal(e.getMessage()));
				return 0;
			}
		}
		sendCannotFindBuildArea(context, name);
		return 0;
	}

	private static int makeBook(CommandContext<CommandSourceStack> context) {
		return makeBook(context, "GDMC");
	}

	private static int makeBook(CommandContext<CommandSourceStack> context, String author) {
		ArrayList<BuildAreaInstance> savedBuildAreas = BuildArea.getSavedBuildAreas(context.getSource().getServer());
		if (savedBuildAreas.isEmpty()) {
			Feedback.sendSuccess(
				context,
				Feedback.chatMessage("No saved build areas")
			);
			return 0;
		}
		JsonArray pageLines = new JsonArray();
		JsonObject title = new JsonObject();
		title.addProperty("text", "Locations\n");
		title.addProperty("bold", true);
		pageLines.add(title);
		pageLines.add("\n");
		for (BuildAreaInstance buildArea : savedBuildAreas) {
			pageLines.add(" • ");
			JsonObject line = writeBookLine(buildArea);
			pageLines.add(line);
		}
		String bookContentValue = String.format(
			"{title:%s,author:%s,pages:[[[%s]]]}",
			"Locations",
			author,
			pageLines
		);

		try {
			context.getSource().dispatcher().execute(
				"give @s written_book[written_book_content=" + bookContentValue + ",custom_name=[\"\",{\"text\":\"Locations\",\"italic\":false,\"color\":\"dark_aqua\"}]]",
				context.getSource()
			);
		} catch (CommandSyntaxException e) {
			Feedback.sendFailure(context, Component.literal(e.getMessage()));
			return 0;
		}
		return 1;
	}

	private static JsonObject writeBookLine(BuildAreaInstance buildArea) {
		JsonObject line = new JsonObject();
		line.addProperty("text",  buildArea.name + "\n");
		line.addProperty("color", "dark_green");
		line.addProperty("underlined", true);
		line.addProperty("bold", false);
		JsonObject clickEvent = new JsonObject();
		clickEvent.addProperty("action", "run_command");
		clickEvent.addProperty(
			"command",
			String.format(
				"tp @s %s %s %s %s 0",
				buildArea.spawnPos.getX(),
				buildArea.spawnPos.getY(),
				buildArea.spawnPos.getZ(),
				buildArea.spawnLookRotation
			)
		);
		line.add("click_event", clickEvent);
		JsonObject hoverEvent = new JsonObject();
		hoverEvent.addProperty("action", "show_text");
		hoverEvent.addProperty(
			"value",
			String.format(
				"%s\n%s",
				buildArea.spawnPos.toShortString(),
				Component.translatable("chat.coordinates.tooltip").getString()
			)
		);
		line.add("hover_event", hoverEvent);
		return line;
	}

	private static MutableComponent buildAreaNameText(String name) {
		return Component.empty().append(Component.literal(name).withStyle(ChatFormatting.GREEN)).append(" ");
	}

	private static MutableComponent buildAreaCopyText(BuildAreaInstance buildArea) {
		return buildAreaNameText(buildArea.name).append(
			Feedback.copyOnClickText(
				String.format("from %s to %s", buildArea.from.toShortString(), buildArea.to.toShortString()),
				buildArea.toJSONString()
			)
		);
	}

	private static void sendCurrentBuildArea(CommandContext<CommandSourceStack> context, BuildAreaInstance buildArea) {
		Feedback.sendSuccess(
			context,
			Feedback.chatMessage("Current build area: ").append(buildAreaCopyText(buildArea))
		);
	}

	private static void sendCannotFindBuildArea(CommandContext<CommandSourceStack> context, String name) {
		Feedback.sendFailure(
			context,
			Feedback.chatMessage(String.format("Cannot find build area with name \"%s\"", name))
		);
	}
}

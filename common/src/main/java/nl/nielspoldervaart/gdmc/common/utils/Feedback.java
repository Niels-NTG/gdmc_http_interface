package nl.nielspoldervaart.gdmc.common.utils;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;

public class Feedback {

	public static MutableComponent chatMessage(String str) {
		return modNamePrefix().append(str);
	}

	private static MutableComponent modNamePrefix() {
		return ComponentUtils.wrapInSquareBrackets(Component.literal("GDMC-HTTP").withStyle(ChatFormatting.DARK_AQUA)).append(" ");
	}

	public static MutableComponent copyOnClickText(String str) {
		return copyOnClickText(str, str);
	}

	public static MutableComponent copyOnClickText(String str, String clipboardContent) {
		return Component.literal(str).withStyle(
			(style) -> style.withUnderlined(true).withClickEvent(
				new ClickEvent.CopyToClipboard(clipboardContent)
			).withHoverEvent(
				new HoverEvent.ShowText(Component.translatable("chat.copy.click"))
			).withInsertion(str)
		);
	}

	public static void sendSuccess(CommandContext<CommandSourceStack> commandSourceContext, MutableComponent message) {
		CommandSourceStack source = commandSourceContext.getSource();
		source.sendSuccess(() -> message, true);
	}

}

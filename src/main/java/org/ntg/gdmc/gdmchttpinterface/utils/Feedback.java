package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.ChatFormatting;
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
		return Component.literal(str).withStyle((style) -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboardContent)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click"))).withInsertion(str));
	}

}

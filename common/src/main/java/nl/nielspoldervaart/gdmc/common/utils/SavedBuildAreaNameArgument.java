package nl.nielspoldervaart.gdmc.common.utils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

public class SavedBuildAreaNameArgument implements ArgumentType<String> {

	public static MinecraftServer server;

	public SavedBuildAreaNameArgument() {}

	@Override
	public String parse(final StringReader reader) throws CommandSyntaxException {
		final String text = reader.getRemaining();
		reader.setCursor(reader.getTotalLength());
		return text;
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(
			BuildArea.getSavedBuildAreaKeys(server),
			builder
		);
	}
}

package nl.nielspoldervaart.gdmc.common.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nl.nielspoldervaart.gdmc.common.utils.TagUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PlayersHandler extends HandlerBase {

    public PlayersHandler(MinecraftServer mcServer) {
        super(mcServer);
    }
    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        if (!httpExchange.getRequestMethod().equalsIgnoreCase("get")) {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        JsonArray responseList = new JsonArray();

        // Query parameters
        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        // GET: Search players using a Target Selector (https://minecraft.wiki/w/Target_selectors).
        // Defaults to "@a" (find all players).
        String playerSelectorString;

        // GET: Whether to include entity data https://minecraft.wiki/w/Entity_format#Entity_Format
        boolean includeData;

        // GET: Search for players within a specific dimension. Only works if selector string contains
        // position arguments.
        String dimension;

        try {
            playerSelectorString = queryParams.getOrDefault("selector", "@a");

            includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));

            dimension = queryParams.getOrDefault("dimension", null);
        } catch (NumberFormatException e) {
            throw new HttpException("Could not parse query parameter: " + e.getMessage(), 400);
        }

        StringReader playerSelectorStringReader = new StringReader(playerSelectorString);
        try {
            EntitySelector playerSelector = EntityArgument.players().parse(playerSelectorStringReader);
            CommandSourceStack cmdSrc = createCommandSource("GDMC-PlayersHandler", getServerLevel(dimension));

            List<ServerPlayer> players = playerSelector.findPlayers(cmdSrc);
            // Add each player's name, UUID and additional data to the response list.
            for (ServerPlayer player : players) {
                JsonObject json = new JsonObject();
                // Name and UUID.
                json.addProperty("name", player.getName().getString());
                json.addProperty("uuid", player.getStringUUID());
                // All player NBT data if requested
                if (includeData) {
                    json.addProperty("data", TagUtils.serializeEntityNBT(player).getAsString());
                }
                responseList.add(json);
            }
        } catch (CommandSyntaxException e) {
            throw new HttpException("Malformed player target selector: " + e.getMessage(), 400);
        }

        // Response headers
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, responseList.toString());
    }
}

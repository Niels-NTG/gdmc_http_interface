package com.gdmc.httpinterfacemod.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PlayersHandler extends HandlerBase {
    public PlayersHandler(MinecraftServer mcServer) { super(mcServer); }

    private boolean includeData;

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        String method = httpExchange.getRequestMethod().toLowerCase();

        JsonArray responseList = new JsonArray();

        if (method.equals("get")) {

            // Query parameters
            Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

            // Check if they want all data included
            try {
                includeData = Boolean.parseBoolean(queryParams.getOrDefault("includeData", "false"));
            } catch (NumberFormatException e) {
                String message = "Could not parse query parameter: " + e.getMessage();
                throw new HttpException(message, 400);
            }

            // Get the player list
            PlayerList playerList = mcServer.getPlayerList();

            // Get a collection of all the players on the server
            List<ServerPlayer> players = playerList.getPlayers();

            // Add each player's name, UUID and additional data to the response list.
            for (ServerPlayer player : players) {
                JsonObject json = new JsonObject();
                // Name and UUID.
                json.addProperty("name", player.getName().getString());
                json.addProperty("uuid", player.getStringUUID());
                // All player NBT data if requested
                if (includeData) {
                    json.addProperty("data", player.serializeNBT().getAsString());
                }
                responseList.add(json);
            }
        } else {
            throw new HttpException("Method not allowed. Only GET requests are supported.", 405);
        }

        // Response headers
        Headers responseHeaders = httpExchange.getResponseHeaders();
        setDefaultResponseHeaders(responseHeaders);

        resolveRequest(httpExchange, responseList.toString());
    }
}

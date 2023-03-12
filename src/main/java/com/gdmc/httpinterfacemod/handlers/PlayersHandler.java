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

public class PlayersHandler extends HandlerBase {
    public PlayersHandler(MinecraftServer mcServer) { super(mcServer); }

    @Override
    protected void internalHandle(HttpExchange httpExchange) throws IOException {

        String method = httpExchange.getRequestMethod().toLowerCase();

        JsonArray responseList = new JsonArray();

        if (method.equals("get")) {

            // Get the player list
            PlayerList playerList = mcServer.getPlayerList();

            // Get a collection of all the players on the server
            List<ServerPlayer> players = playerList.getPlayers();

            // Add each player's name, position and dimension to the response list
            for (ServerPlayer player : players) {
                JsonObject json = new JsonObject();
                // Name
                json.addProperty("name", player.getName().getString());
                // Position
                var playerPos = player.position();
                json.addProperty("x", playerPos.x);
                json.addProperty("y", playerPos.y);
                json.addProperty("z", playerPos.z);
                // Dimension
                var dimension = player.getLevel().dimension().location().toString();
                json.addProperty("dimension", dimension);
                // Camera rotation
                var cameraRotationVec2 = player.getCamera().getRotationVector();
                JsonObject cameraRotationObject = new JsonObject();
                cameraRotationObject.addProperty("x", cameraRotationVec2.x);
                cameraRotationObject.addProperty("y", cameraRotationVec2.y);
                json.add("cameraRotation", cameraRotationObject);
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

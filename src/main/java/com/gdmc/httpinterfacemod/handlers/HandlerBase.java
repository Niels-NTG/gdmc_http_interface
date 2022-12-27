package com.gdmc.httpinterfacemod.handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class HandlerBase implements HttpHandler {

    public static class HttpException extends RuntimeException {
        public String message;
        public int statusCode;
        public HttpException(String message, int statusCode) {
            this.message = message;
            this.statusCode = statusCode;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();

    MinecraftServer mcServer;
    public HandlerBase(MinecraftServer mcServer) {
        this.mcServer = mcServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            internalHandle(httpExchange);
        } catch (HttpException e) {
            String responseString = e.message;
            byte[] responseBytes = responseString.getBytes(StandardCharsets.UTF_8);
            Headers headers = httpExchange.getResponseHeaders();
            headers.set("Content-Type", "text/plain; charset=UTF-8");

            httpExchange.sendResponseHeaders(e.statusCode, responseBytes.length);
            OutputStream outputStream = httpExchange.getResponseBody();
            outputStream.write(responseBytes);
            outputStream.close();

//            LOGGER.log(Level.ERROR, e.message);
        } catch (Exception e) {
            // create a response string with stacktrace
            String stackTrace = ExceptionUtils.getStackTrace(e);

            String responseString = String.format("Internal server error: %s\n%s", e, stackTrace);
            byte[] responseBytes = responseString.getBytes(StandardCharsets.UTF_8);
            Headers headers = httpExchange.getResponseHeaders();
            headers.set("Content-Type", "text/plain; charset=UTF-8");

            httpExchange.sendResponseHeaders(500, responseBytes.length);
            OutputStream outputStream = httpExchange.getResponseBody();
            outputStream.write(responseBytes);
            outputStream.close();

            LOGGER.log(Level.ERROR, responseString);
            throw e;
        }
    }

    public ServerLevel getServerLevel(String levelName) {
        if (levelName != null) {
            levelName = levelName.toLowerCase();
            if (levelName.equals("nether") || levelName.equals("end")) {
                levelName = "the_" + levelName;
            }
            for (ResourceKey<net.minecraft.world.level.Level> levelResourceKey : mcServer.levelKeys()) {
                if (levelResourceKey.location().getPath().equals(levelName)) {
                    return mcServer.getLevel(levelResourceKey);
                }
            }
        }
        return mcServer.overworld();
    }

    protected abstract void internalHandle(HttpExchange httpExchange) throws IOException;

    protected static void addDefaultHeaders(Headers headers) {
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Content-Disposition", "inline");
    }

    protected static void resolveRequest(HttpExchange httpExchange, String responseString) throws IOException {
        byte[] responseBytes = responseString.getBytes(StandardCharsets.UTF_8);
        resolveRequest(httpExchange, responseBytes);
    }

    protected static void resolveRequest(HttpExchange httpExchange, byte[] responseBytes) throws IOException {
        httpExchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream outputStream = httpExchange.getResponseBody();
        outputStream.write(responseBytes);
        outputStream.close();
    }

    protected static String getHeader(Headers headers, String key, String defaultValue) {
        List<String> list = headers.get(key);
        if(list == null || list.size() == 0)
            return defaultValue;
        else
            return list.get(0);
    }

    protected static Map<String, String> parseQueryString(String qs) {
        Map<String, String> result = new HashMap<>();
        if (qs == null)
            return result;

        int last = 0, next, l = qs.length();
        while (last < l) {
            next = qs.indexOf('&', last);
            if (next == -1)
                next = l;

            if (next > last) {
                int eqPos = qs.indexOf('=', last);
                if (eqPos < 0 || eqPos > next)
                    result.put(URLDecoder.decode(qs.substring(last, next), StandardCharsets.UTF_8), "");
                else
                    result.put(URLDecoder.decode(qs.substring(last, eqPos), StandardCharsets.UTF_8), URLDecoder.decode(qs.substring(eqPos + 1, next), StandardCharsets.UTF_8));
            }
            last = next + 1;
        }
        return result;
    }

    protected CommandSourceStack createCommandSource(String name, MinecraftServer mcServer, String dimension) {
        CommandSource commandSource = new CommandSource() {
            @Override
            public void sendSystemMessage(@NotNull Component p_230797_) {

            }

            @Override
            public boolean acceptsSuccess() {
                return false;
            }

            @Override
            public boolean acceptsFailure() {
                return false;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };

        return new CommandSourceStack(
            commandSource,
            new Vec3(0, 0, 0),
            new Vec2(0, 0),
            this.getServerLevel(dimension),
            4,
            name,
            Component.nullToEmpty(name),
            mcServer,
            null
        );
    }
}

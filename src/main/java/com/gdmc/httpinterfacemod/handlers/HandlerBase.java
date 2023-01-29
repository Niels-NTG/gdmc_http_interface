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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class HandlerBase implements HttpHandler {

    public static class HttpException extends RuntimeException {
        public final String message;
        public final int statusCode;
        public HttpException(String message, int statusCode) {
            this.message = message;
            this.statusCode = statusCode;
        }
    }

    protected static final Logger LOGGER = LogManager.getLogger();

    final MinecraftServer mcServer;
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

            LOGGER.log(Level.ERROR, e.message);
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

    /**
     * Get specific level (sometimes called a "dimension") of a Minecraft world.
     * In a conventional Minecraft world the following levels are expected to be
     * present: {@code "overworld"}, {@code "the_nether"} and {@code "the_end"}.
     * A world may be modified to have a different list of levels.
     *
     * @param levelName     name of level as it's path name appears in world's
     *                      list of levels. For convenience "the_nether" and
     *                      "the_end" can be shorted to "nether" and "end"
     *                      respectively but still return the same level.
     *                      If no level with the given name is found or if the
     *                      given name is {@code null}, return the overworld.
     * @return              A level on {@link #mcServer}
     */
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

    /**
     * Method that an endpoint handler class can use for executing a function.
     *
     * @param httpExchange  HTTP request exhanger
     * @throws IOException  Any errors caught should be dealt with in {@link #handle(HttpExchange)}.
     */
    protected abstract void internalHandle(HttpExchange httpExchange) throws IOException;

    protected static String getHeader(Headers headers, String key, String defaultValue) {
        List<String> list = headers.get(key);
        if(list == null || list.size() == 0)
            return defaultValue;
        else
            return list.get(0);
    }

    /**
     * @param header single header as string from request or response headers
     * @return {@code true} if header string has a common description of a JSON Content-Type.
     */
    protected static boolean hasJsonTypeInHeader(String header) {
        return header.equals("application/json") || header.equals("text/json");
    }

    /**
     * Helper to add basic headers to headers of a response.
     *
     * @param headers request or response headers
     */
    protected static void setDefaultResponseHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Content-Disposition", "inline");
    }

    /**
     * Helper to tell clients that response is formatted as JSON.
     *
     * @param headers request or response headers
     */
    protected static void setResponseHeadersContentTypeJson(Headers headers) {
        headers.set("Content-Type", "application/json; charset=UTF-8");
    }

    /**
     * Helper to tell clients that response is formatted as plain text.
     *
     * @param headers request or response headers
     */
    protected static void setResponseHeadersContentTypePlain(Headers headers) {
        headers.set("Content-Type", "text/plain; charset=UTF-8");
    }

    /**
     * Helper to tell clients that response is in a binary format that should be treated as an attachment.
     *
     * @param headers request or response headers
     * @param isCompressed true if file is gzip-compressed
     */
    protected static void setResponseHeadersContentTypeBinary(Headers headers, boolean isCompressed) {
        headers.set("Content-Type", "application/octet-stream");
        headers.set("Content-Disposition", "attachment");
        if (isCompressed) {
            headers.set("Content-Encoding", "gzip");
        }
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

    /**
     * Parse URL query string from {@code httpExchange.getRequestURI().getRawQuery()} into a convenient Map.
     *
     * @param qs    Any string, preferably the query section from an URL.
     * @return      Map of String-String pairs.
     */
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

    /**
     * Helper to create a {@link CommandSourceStack}, which serves as the source to dispatch
     * commands from (See {@link CommandHandler}) or as a point of origin to place blocks
     * relative from (See {@link BlocksHandler}).
     *
     * @param name          Some unique identifier.
     * @param dimension     The dimension (also known as level) in the world of {@code mcServer} in which the {@link CommandSourceStack} is going to be placed.
     * @return              An instance of {@link CommandSourceStack}.
     */
    protected CommandSourceStack createCommandSource(String name, String dimension) {
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

    /**
     * Helper to create a {@link LivingEntity}, which can be used block placement context.
     * Such a context is required for doing operations after placing certain blocks that have an implementation of the {@code setPlacedBy} method.
     * This applies to blocks that come in multiple parts such as beds ({@link net.minecraft.world.level.block.BedBlock}) and doors ({@link net.minecraft.world.level.block.DoorBlock}).
     *
     * @param dimension The dimension (also known as level) in the world of {@code mcServer} in which the {@link LivingEntity} is going to be placed.
     * @return An instance of {@link LivingEntity}
     */
    protected LivingEntity createLivingEntity(String dimension) {
        return new LivingEntity(EntityType.PLAYER, getServerLevel(dimension)) {

            @Override
            public Iterable<ItemStack> getArmorSlots() {
                return null;
            }

            @Override
            public ItemStack getItemBySlot(EquipmentSlot p_21127_) {
                return null;
            }

            @Override
            public void setItemSlot(EquipmentSlot p_21036_, ItemStack p_21037_) {

            }

            @Override
            public HumanoidArm getMainArm() {
                return null;
            }
        };
    }
}

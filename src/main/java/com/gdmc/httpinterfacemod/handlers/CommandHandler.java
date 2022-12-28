package com.gdmc.httpinterfacemod.handlers;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CommandHandler extends HandlerBase {
    private static final Logger LOGGER = LogManager.getLogger();

    public CommandHandler(MinecraftServer mcServer) {
        super(mcServer);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        Map<String, String> queryParams = parseQueryString(httpExchange.getRequestURI().getRawQuery());

        String dimension = queryParams.getOrDefault("dimension", null);

        // execute command(s)
        InputStream bodyStream = httpExchange.getRequestBody();
        List<String> commands = new BufferedReader(new InputStreamReader(bodyStream))
                .lines().filter(a -> a.length() > 0).toList();

        CommandSourceStack cmdSrc = createCommandSource("GDMC-CommandHandler", mcServer, dimension);

        List<String> outputs = new ArrayList<>();
        for (String command: commands) {
            // requests to run the actual command execution on the main thread
            CompletableFuture<String> cfs = CompletableFuture.supplyAsync(() -> {
                String str;
                try {
                    str = "" + mcServer.getCommands().getDispatcher().execute(command, cmdSrc);
                } catch (CommandSyntaxException e) {
                    LOGGER.error(e.getMessage());
                    str = e.getMessage();
                }
                return str;
            }, mcServer);

            // block this thread until the above code has run on the main thread
            String result = cfs.join();
            outputs.add(result);
        }

        // Response headers
        Headers responseHeaders = httpExchange.getResponseHeaders();
        addDefaultResponseHeaders(responseHeaders);
        addResponseHeadersContentTypePlain(responseHeaders);

        // body
        String responseString = String.join("\n", outputs);
        resolveRequest(httpExchange, responseString);
    }
}
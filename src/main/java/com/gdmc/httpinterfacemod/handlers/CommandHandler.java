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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommandHandler extends HandlerBase {
    private static final Logger LOGGER = LogManager.getLogger();
    private final CommandSourceStack cmdSrc;

    public CommandHandler(MinecraftServer mcServer) {
        super(mcServer);
        cmdSrc = createCommandSource("GDMC-CommandHandler", mcServer);
    }

    @Override
    public void internalHandle(HttpExchange httpExchange) throws IOException {

        // execute command(s)
        InputStream bodyStream = httpExchange.getRequestBody();
        List<String> commands = new BufferedReader(new InputStreamReader(bodyStream))
                .lines().filter(a -> a.length() > 0).collect(Collectors.toList());

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

        //headers
        Headers headers = httpExchange.getResponseHeaders();
        headers.add("Content-Type", "text/plain; charset=UTF-8");

        // body
        String responseString = String.join("\n", outputs);
        resolveRequest(httpExchange, responseString);
    }
}
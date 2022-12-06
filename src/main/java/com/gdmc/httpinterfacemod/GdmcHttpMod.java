package com.gdmc.httpinterfacemod;

import com.gdmc.httpinterfacemod.utils.RegistryHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("gdmchttp")
public class GdmcHttpMod
{
    public static final String MODID = "gdmchttp";

    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public GdmcHttpMod() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
        RegistryHandler.registerCommands(event);
        MinecraftServer minecraftServer = event.getServer();

        try {
            GdmcHttpServer.startServer(minecraftServer);
            minecraftServer.sendSystemMessage(Component.nullToEmpty("GDMC Server started successfully."));
        } catch (IOException e) {
            LOGGER.warn("Unable to start server!");
            minecraftServer.sendSystemMessage(Component.nullToEmpty("GDMC Server failed to start!"));
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("HELLO from server stopping");

        GdmcHttpServer.stopServer();
    }
}

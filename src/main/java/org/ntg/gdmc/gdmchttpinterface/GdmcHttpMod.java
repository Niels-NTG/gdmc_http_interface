package org.ntg.gdmc.gdmchttpinterface;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntg.gdmc.gdmchttpinterface.config.GdmcHttpConfig;
import org.ntg.gdmc.gdmchttpinterface.utils.RegistryHandler;

import java.io.IOException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("gdmchttpinterface")
public class GdmcHttpMod
{
    public static final String MODID = "gdmchttpinterface";

    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public GdmcHttpMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GdmcHttpConfig.SPEC, "gdmc-http-interface-common.toml");
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("Server starting");

        RegistryHandler.registerCommands(event);
        MinecraftServer minecraftServer = event.getServer();

        try {
            GdmcHttpServer.startServer(minecraftServer);
            minecraftServer.sendSystemMessage(successMessage());
        } catch (IOException e) {
            LOGGER.warn("Unable to start server!");
            minecraftServer.sendSystemMessage(failureMessage());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping");

        GdmcHttpServer.stopServer();
    }

    @SubscribeEvent
    public void onPlayerLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() != null) {
            event.getEntity().displayClientMessage(GdmcHttpServer.hasHtppServerStarted ? successMessage() : failureMessage(), true);
        }
    }

    private static Component successMessage() {
        return Component.nullToEmpty(String.format("GDMC-HTTP server started successfully at http://localhost:%s/", GdmcHttpServer.getCurrentHttpPort()));
    }

    private static Component failureMessage() {
        return Component.nullToEmpty("GDMC-HTTP server failed to start!");
    }
}

package nl.nielspoldervaart.gdmc.forge;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;
import nl.nielspoldervaart.gdmc.forge.utils.RegistryHandler;
import nl.nielspoldervaart.gdmc.forge.config.GdmcHttpConfig;

import java.io.IOException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("gdmc_http_interface")
public final class GdmcHttpMod
{
    @SuppressWarnings("unused")
    public static final String MODID = "gdmc_http_interface";

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
            ForgeGdmcHttpServer.startServer(minecraftServer);
            minecraftServer.sendSystemMessage(successMessage());
        } catch (IOException e) {
            LOGGER.warn("Unable to start server!");
            minecraftServer.sendSystemMessage(failureMessage());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping");

        ForgeGdmcHttpServer.stopServer();
    }

    @SubscribeEvent
    public void onPlayerLogIn(PlayerLoggedInEvent event) {
        if (event.getEntity() != null) {
            event.getEntity().displayClientMessage(ForgeGdmcHttpServer.hasHttpServerStarted ? successMessage() : failureMessage(), true);
        }
    }

    private static MutableComponent successMessage() {
        return Feedback.chatMessage("Server started at ").append(Feedback.copyOnClickText(String.format("http://localhost:%s/", ForgeGdmcHttpServer.getCurrentHttpPort())));
    }

    private static MutableComponent failureMessage() {
        return Feedback.chatMessage("GDMC-HTTP server failed to start!");
    }
}

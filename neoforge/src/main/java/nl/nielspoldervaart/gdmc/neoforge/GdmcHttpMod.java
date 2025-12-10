package  nl.nielspoldervaart.gdmc.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import nl.nielspoldervaart.gdmc.common.commands.GetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.common.commands.SetBuildAreaCommand;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;
import nl.nielspoldervaart.gdmc.neoforge.commands.SetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.neoforge.config.GdmcHttpConfig;
import org.slf4j.Logger;

import java.io.IOException;

@Mod(GdmcHttpMod.MODID)
public class GdmcHttpMod {

	public static final String MODID = "gdmc_http_interface";

	private static final Logger logger = LogUtils.getLogger();

	public GdmcHttpMod(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, GdmcHttpConfig.SPEC);

		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
		logger.info("Server starting");

		registerCommands(event);
		MinecraftServer minecraftServer = event.getServer();

		try {
			NeoForgeGdmcHttpServer.startServer(minecraftServer);
			minecraftServer.sendSystemMessage(successMessage());
		} catch (IOException e) {
			logger.warn("Unable to start server!");
			minecraftServer.sendSystemMessage(failureMessage());
		}
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		logger.info("Server stopping");
		NeoForgeGdmcHttpServer.stopServer();
	}

	@SubscribeEvent
	public void onPlayerLogIn(PlayerEvent.PlayerLoggedInEvent event) {
		event.getEntity().displayClientMessage(NeoForgeGdmcHttpServer.hasHttpServerStarted ? successMessage() : failureMessage(), true);
	}

	private static Component successMessage() {
		return Feedback.chatMessage("Server started at ").append(
			Feedback.copyOnClickText(String.format("http://localhost:%s/", NeoForgeGdmcHttpServer.getCurrentHttpPort()))
		);
	}

	private static Component failureMessage() {
		return Feedback.chatMessage("GDMC-HTTP server failed to start!");
	}

	private static void registerCommands(ServerStartingEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
		SetBuildAreaCommand.register(dispatcher);
		SetHttpInterfacePort.register(dispatcher);
		GetHttpInterfacePort.register(dispatcher);
	}

}

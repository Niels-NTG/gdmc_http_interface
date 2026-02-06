package  nl.nielspoldervaart.gdmc.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import nl.nielspoldervaart.gdmc.common.commands.GetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.common.commands.BuildAreaCommand;
import nl.nielspoldervaart.gdmc.common.utils.BuildArea;
import nl.nielspoldervaart.gdmc.common.utils.Feedback;
import nl.nielspoldervaart.gdmc.common.utils.SavedBuildAreaNameArgument;
import nl.nielspoldervaart.gdmc.neoforge.commands.SetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.neoforge.config.GdmcHttpConfig;
import org.slf4j.Logger;

import java.io.IOException;

@Mod(GdmcHttpMod.MODID)
public class GdmcHttpMod {

	public static final String MODID = "gdmc_http_interface";

	private static final Logger logger = LogUtils.getLogger();

	public GdmcHttpMod(ModContainer modContainer, IEventBus eventBus) {
		modContainer.registerConfig(ModConfig.Type.COMMON, GdmcHttpConfig.SPEC);

		NeoForge.EVENT_BUS.register(this);

		eventBus.addListener(this::registerCustomArgumentType);
	}

	private void registerCustomArgumentType(RegisterEvent event) {
		event.register(
			BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(),
			Identifier.fromNamespaceAndPath(MODID, "saved_build_area_name_argument"),
			() -> ArgumentTypeInfos.registerByClass(
				SavedBuildAreaNameArgument.class,
				SingletonArgumentInfo.contextFree(SavedBuildAreaNameArgument::new)
			)
		);
	}

	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
		logger.info("Server starting");

		registerCommands(event.getServer());
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
		event.getEntity().displayClientMessage(
			NeoForgeGdmcHttpServer.hasHttpServerStarted ? successMessage() : failureMessage(),
			true
		);
		// Set build area if "default" is still present in server command storage.
		BuildArea.setCurrentBuildAreaFromStorage(event.getEntity().level().getServer(), "default");
	}

	private static Component successMessage() {
		return Feedback.chatMessage("Server started at ").append(
			Feedback.copyOnClickText(String.format("http://localhost:%s/", NeoForgeGdmcHttpServer.getCurrentHttpPort()))
		);
	}

	private static Component failureMessage() {
		return Feedback.chatMessage("GDMC-HTTP server failed to start!");
	}

	private static void registerCommands(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		SavedBuildAreaNameArgument.server = server;
		BuildAreaCommand.register(dispatcher);
		SetHttpInterfacePort.register(dispatcher);
		GetHttpInterfacePort.register(dispatcher);
	}

}

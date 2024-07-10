package nl.nielspoldervaart.gdmc.fabric.utils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import nl.nielspoldervaart.gdmc.common.commands.SetBuildAreaCommand;
import nl.nielspoldervaart.gdmc.common.commands.GetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.fabric.commands.SetHttpInterfacePort;

public final class RegistryHandler {

	public static void registerCommands(MinecraftServer minecraftServer) {
		CommandDispatcher<CommandSourceStack> dispatcher = minecraftServer.getCommands().getDispatcher();
		SetBuildAreaCommand.register(dispatcher);
		SetHttpInterfacePort.register(dispatcher);
		GetHttpInterfacePort.register(dispatcher);
	}
}

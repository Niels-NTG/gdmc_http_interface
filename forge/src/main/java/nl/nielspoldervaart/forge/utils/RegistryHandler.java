package nl.nielspoldervaart.forge.utils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.server.ServerStartingEvent;
import nl.nielspoldervaart.gdmc.commands.GetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.commands.SetBuildAreaCommand;
import nl.nielspoldervaart.forge.commands.SetHttpInterfacePort;

public final class RegistryHandler {

    public static void registerCommands(ServerStartingEvent event) {

        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        SetBuildAreaCommand.register(dispatcher);
        SetHttpInterfacePort.register(dispatcher);
        GetHttpInterfacePort.register(dispatcher);
    }
}

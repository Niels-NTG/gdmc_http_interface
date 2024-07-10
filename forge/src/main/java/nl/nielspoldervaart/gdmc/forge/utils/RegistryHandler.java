package nl.nielspoldervaart.gdmc.forge.utils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.server.ServerStartingEvent;
import nl.nielspoldervaart.gdmc.common.commands.GetHttpInterfacePort;
import nl.nielspoldervaart.gdmc.common.commands.SetBuildAreaCommand;
import nl.nielspoldervaart.gdmc.forge.commands.SetHttpInterfacePort;

public final class RegistryHandler {

    public static void registerCommands(ServerStartingEvent event) {

        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        SetBuildAreaCommand.register(dispatcher);
        SetHttpInterfacePort.register(dispatcher);
        GetHttpInterfacePort.register(dispatcher);
    }
}

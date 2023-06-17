package org.ntg.gdmc.gdmchttpinterface.utils;

import org.ntg.gdmc.gdmchttpinterface.commands.GetHttpInterfacePort;
import org.ntg.gdmc.gdmchttpinterface.commands.SetBuildAreaCommand;
import org.ntg.gdmc.gdmchttpinterface.commands.SetHttpInterfacePort;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.server.ServerStartingEvent;

public class RegistryHandler {

    public static void registerCommands(ServerStartingEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        SetBuildAreaCommand.register(dispatcher);
        SetHttpInterfacePort.register(dispatcher);
        GetHttpInterfacePort.register(dispatcher);
    }
}

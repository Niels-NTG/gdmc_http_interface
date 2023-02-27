package com.gdmc.httpinterfacemod.utils;

import com.gdmc.httpinterfacemod.commands.GetHttpInterfacePort;
import com.gdmc.httpinterfacemod.commands.SetBuildAreaCommand;
import com.gdmc.httpinterfacemod.commands.SetHttpInterfacePort;
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

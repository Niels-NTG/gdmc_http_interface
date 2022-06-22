package com.gdmc.httpinterfacemod.utils;

import com.gdmc.httpinterfacemod.settlementcommand.SetBuildAreaCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.server.ServerStartingEvent;

public class RegistryHandler {

    public static void registerCommands(ServerStartingEvent event) {

        // TODO might be wrong (didn't test)
        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        SetBuildAreaCommand.register(dispatcher);
        // maybe try this instead:
//        CommandDispatcher<CommandSource> dispatcher = event.getServer().getFunctionManager().getCommandDispatcher();
//        BuildSettlementCommand.register(dispatcher);
    }
}

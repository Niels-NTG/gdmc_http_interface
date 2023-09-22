package org.ntg.gdmc.gdmchttpinterface.utils;

import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class CustomCommandSource implements CommandSource {

    private MutableComponent lastOutput;

    @Override
    public void sendSystemMessage(Component output) {
        this.lastOutput = output.copy();
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public MutableComponent getLastOutput() {
        return this.lastOutput;
    }
}

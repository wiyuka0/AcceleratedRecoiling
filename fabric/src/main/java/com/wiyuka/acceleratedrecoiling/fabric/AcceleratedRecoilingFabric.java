package com.wiyuka.acceleratedrecoiling.fabric;

import com.wiyuka.acceleratedrecoiling.natives.NativeInterface;
import net.fabricmc.api.ModInitializer;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class AcceleratedRecoilingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _)
                -> AcceleratedRecoiling.init(dispatcher));

        // register server stop event
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> NativeInterface.destroy());
    }
}

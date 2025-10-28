package com.wiyuka.acceleratedRecoiling.listeners;

import com.wiyuka.acceleratedRecoiling.AcceleratedRecoiling;
import com.wiyuka.acceleratedRecoiling.natives.NativeInterface;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = AcceleratedRecoiling.MODID)
public class ServerStop {
    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
//        MinecraftServer server = event.getServer();
        NativeInterface.destroy();
    }
}

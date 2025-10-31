package com.wiyuka.acceleratedrecoiling.listeners;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import com.wiyuka.acceleratedrecoiling.natives.NativeInterface;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AcceleratedRecoiling.MODID)
public class ServerStop {
    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
//        MinecraftServer server = event.getServer();
        NativeInterface.destroy();
    }
}

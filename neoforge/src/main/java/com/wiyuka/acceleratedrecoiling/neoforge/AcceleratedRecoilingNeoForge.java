package com.wiyuka.acceleratedrecoiling.neoforge;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(AcceleratedRecoiling.MODID)
public class AcceleratedRecoilingNeoForge {
    public AcceleratedRecoilingNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AcceleratedRecoiling.init(event.getDispatcher());
    }
}

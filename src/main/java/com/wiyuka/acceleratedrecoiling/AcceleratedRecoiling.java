package com.wiyuka.acceleratedrecoiling;

import com.mojang.logging.LogUtils;
import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(AcceleratedRecoiling.MODID)
public class AcceleratedRecoiling {
    public static final String MODID = "acceleratedrecoiling";
    public static final Logger LOGGER = LogUtils.getLogger();
    public AcceleratedRecoiling(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 调用你的指令类的注册方法
        ToggleFoldCommand.register(event.getDispatcher());
    }
}

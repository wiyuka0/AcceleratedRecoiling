package com.wiyuka.acceleratedrecoiling;

import com.mojang.logging.LogUtils;
import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;


// The value here should match an entry in the META-INF/mods.toml file

@Mod(AcceleratedRecoiling.MODID)
@Mod.EventBusSubscriber(modid = AcceleratedRecoiling.MODID)
public class AcceleratedRecoiling {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "acceleratedrecoiling";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public AcceleratedRecoiling() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 调用你的指令类的注册方法
        ToggleFoldCommand.register(event.getDispatcher());
    }
}

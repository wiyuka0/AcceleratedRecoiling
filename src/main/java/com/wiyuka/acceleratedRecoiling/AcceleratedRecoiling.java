package com.wiyuka.acceleratedRecoiling;

import com.mojang.logging.LogUtils;
import com.wiyuka.acceleratedRecoiling.commands.ToggleFoldCommand;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AcceleratedRecoiling.MODID)
public class AcceleratedRecoiling {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "acceleratedrecoiling";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public AcceleratedRecoiling(IEventBus modEventBus, ModContainer modContainer) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 调用你的指令类的注册方法
        ToggleFoldCommand.register(event.getDispatcher());
    }
}

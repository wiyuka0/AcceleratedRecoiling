package com.wiyuka.acceleratedrecoiling;

import com.mojang.logging.LogUtils;
import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.listeners.ServerStop;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;

public class AcceleratedRecoiling implements ModInitializer {
    public static final String MODID = "acceleratedrecoiling";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        FoldConfig.loadConfig();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ToggleFoldCommand.register(dispatcher);
        });

        ServerStop.register();

        LOGGER.info("AcceleratedRecoiling Initialized!");
    }
}
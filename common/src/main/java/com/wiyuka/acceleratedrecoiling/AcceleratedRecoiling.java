package com.wiyuka.acceleratedrecoiling;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;

public class AcceleratedRecoiling {
    public static final String MODID = "acceleratedrecoiling";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 调用你的指令类的注册方法
        ToggleFoldCommand.register(dispatcher);
    }
}

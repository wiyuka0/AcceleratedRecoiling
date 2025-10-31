package com.wiyuka.acceleratedrecoiling.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wiyuka.acceleratedrecoiling.natives.ParallelAABB;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ToggleFoldCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("togglefold")
                        .requires(source -> source.hasPermission(2))
                        .executes(ToggleFoldCommand::execute)

        );
    }

    // 指令执行方法
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ParallelAABB.useFold = !ParallelAABB.useFold;

        source.sendSuccess(() -> Component.literal(STR."Toggle FOLD to \{ParallelAABB.useFold}"), false);

        return 1;
    }
}

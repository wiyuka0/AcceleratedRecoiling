package com.wiyuka.acceleratedrecoiling.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
//    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
//        dispatcher.register(
//                Commands.literal("togglefold")
//                        .requires(source -> source.hasPermission(2))
//                        .executes(ToggleFoldCommand::execute)
//
//        );
//    }
//
//    // 指令执行方法
//    private static int execute(CommandContext<CommandSourceStack> context) {
//        CommandSourceStack source = context.getSource();
//
//        ParallelAABB.useFold = !ParallelAABB.useFold;
//
//        source.sendSuccess(() -> Component.literal("Toggle FOLD to " + ParallelAABB.useFold), false);
//
//        return 1;
//    }

import net.minecraft.ChatFormatting;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class ToggleFoldCommand {
    private static final String[] COMMAND_ALIAS = {"acceleratedrecoiling", "togglefold"};

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String commandAlias : COMMAND_ALIAS) registerCommand(dispatcher, commandAlias);
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(
                Commands.literal(name)
                        .requires(source -> source.hasPermission(2)) // 同样需要2级权限


                        .then(Commands.literal("check")
                                .executes(ToggleFoldCommand::checkConfig)
                        )


                        .then(Commands.literal("enableEntityCollision")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ToggleFoldCommand::setEnableEntityCollision)
                                )
                        )


                        .then(Commands.literal("enableEntityGetterOptimization")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ToggleFoldCommand::setEnableEntityGetter)
                                )
                        )


                        .then(Commands.literal("gridSize")
                                .then(Commands.argument("value", IntegerArgumentType.integer(8))
                                        .executes(ToggleFoldCommand::setGridSize)
                                )
                        )


                        .then(Commands.literal("maxCollision")
                                .then(Commands.argument("value", IntegerArgumentType.integer(16))
                                        .executes(ToggleFoldCommand::setMaxCollision)
                                )
                        )


                        .then(Commands.literal("gpuIndex")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ToggleFoldCommand::setGpuIndex)
                                )
                        )


                        .then(Commands.literal("useCPU")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ToggleFoldCommand::setUseCPU)
                                )
                        )
                        .then(Commands.literal("save")
                                .executes(ToggleFoldCommand::save)
                        )
        );
    }


    private static void sendSuccessMessage(CommandSourceStack source, String configName, Object newValue) {
        var message = Component.literal("Config ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(configName)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" updated to ")
                        .withStyle(ChatFormatting.GRAY));

        if (newValue instanceof Boolean boolValue) {
            message.append(Component.literal(String.valueOf(boolValue))
                    .withStyle(boolValue ? ChatFormatting.GREEN : ChatFormatting.RED));
        } else {
            message.append(Component.literal(String.valueOf(newValue))
                    .withStyle(ChatFormatting.AQUA));
        }

        source.sendSuccess(() -> message, false);
    }


    private static Component buildConfigLine(String configName, Object value) {
        var line = Component.literal("  " + configName + ": ")
                .withStyle(ChatFormatting.GRAY);

        if (value instanceof Boolean boolValue) {
            line.append(Component.literal(String.valueOf(boolValue))
                    .withStyle(boolValue ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        } else { // Integer
            line.append(Component.literal(String.valueOf(value))
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }

        line.append("\n");
        return line;
    }




    private static int checkConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        var message = Component.literal("Current Accelerated Recoiling Config")
                .withStyle(ChatFormatting.WHITE);

        message.append(Component.literal("\n--------------------\n")
                .withStyle(ChatFormatting.RESET, ChatFormatting.DARK_GRAY));

        message.append(buildConfigLine("enableEntityCollision", FoldConfig.enableEntityCollision));
        message.append(buildConfigLine("enableEntityGetterOptimization", FoldConfig.enableEntityGetterOptimization));
        message.append(buildConfigLine("gridSize", FoldConfig.gridSize));
        message.append(buildConfigLine("maxCollision", FoldConfig.maxCollision));
        message.append(buildConfigLine("gpuIndex", FoldConfig.gpuIndex));
        message.append(buildConfigLine("useCPU", FoldConfig.useCPU));

        message.append(Component.literal("--------------------")
                .withStyle(ChatFormatting.DARK_GRAY));

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class ConfigData {
        @SerializedName("useFold")
        public boolean enableEntityCollision;
        public boolean enableEntityGetterOptimization;
        public int gridSize;
        public int maxCollision;
        public int gpuIndex;
        public boolean useCPU;

        public ConfigData(boolean enableEntityCollision, boolean enableEntityGetterOptimization, int gridSize, int maxCollision, int gpuIndex, boolean useCPU) {
            this.enableEntityCollision = enableEntityCollision;
            this.enableEntityGetterOptimization = enableEntityGetterOptimization;
            this.gridSize = gridSize;
            this.maxCollision = maxCollision;
            this.gpuIndex = gpuIndex;
            this.useCPU = useCPU;
        }
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();


        File targetFile = new File("acceleratedRecoiling.json");

        ConfigData data = new ConfigData(
                FoldConfig.enableEntityCollision,
                FoldConfig.enableEntityGetterOptimization,
                FoldConfig.gridSize,
                FoldConfig.maxCollision,
                FoldConfig.gpuIndex,
                FoldConfig.useCPU
        );


        try (FileWriter writer = new FileWriter(targetFile)) {

            GSON.toJson(data, writer);

            // 4. 发送成功消息
            var message = Component.literal("Config saved ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(targetFile.getName())
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            source.sendSuccess(() -> message, false);

            return 1;

        } catch (IOException e) {

            var message = Component.literal("Failed to save config file: ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(e.getMessage())
                            .withStyle(ChatFormatting.WHITE));
            source.sendFailure(message);

            e.printStackTrace();
            return 0;
        }
    }
    private static int setEnableEntityCollision(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        FoldConfig.enableEntityCollision = value;
        sendSuccessMessage(context.getSource(), "enableEntityCollision", value);
        return 1;
    }
    private static int setEnableEntityGetter(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        FoldConfig.enableEntityGetterOptimization = value;
        sendSuccessMessage(context.getSource(), "enableEntityGetter", value);
        return 1;
    }

    private static int setGridSize(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        FoldConfig.gridSize = value;
        sendSuccessMessage(context.getSource(), "gridSize", value);
        return 1;
    }

    private static int setMaxCollision(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        FoldConfig.maxCollision = value;
        sendSuccessMessage(context.getSource(), "maxCollision", value);
        return 1;
    }

    private static int setGpuIndex(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        FoldConfig.gpuIndex = value;
        sendSuccessMessage(context.getSource(), "gpuIndex", value);
        return 1;
    }

    private static int setUseCPU(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        FoldConfig.useCPU = value;
        sendSuccessMessage(context.getSource(), "useCPU", value);
        return 1;
    }
}
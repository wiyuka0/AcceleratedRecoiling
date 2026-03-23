package com.wiyuka.acceleratedrecoiling.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.NativeInterface;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ToggleFoldCommand {
    private static final String[] COMMAND_ALIAS = {"acceleratedrecoiling", "togglefold"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String commandAlias : COMMAND_ALIAS) {
            registerCommand(dispatcher, commandAlias);
        }
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        LiteralArgumentBuilder<CommandSourceStack> baseCommand = Commands.literal(name)
                .requires(source -> source.hasPermission(2));

        baseCommand.then(Commands.literal("check").executes(ToggleFoldCommand::checkConfig));
        baseCommand.then(Commands.literal("save").executes(ToggleFoldCommand::save));
        baseCommand.then(Commands.literal("updateConfig").executes(ToggleFoldCommand::updateConfig));

        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;

            field.setAccessible(true);
            String fieldName = field.getName();
            Class<?> type = field.getType();

            LiteralArgumentBuilder<CommandSourceStack> fieldNode = Commands.literal(fieldName);

            if (type == boolean.class) {
                fieldNode.executes(ctx -> {
                    try {
                        boolean currentValue = field.getBoolean(null);
                        return setFieldValue(ctx, field, !currentValue);
                    } catch (IllegalAccessException e) {
                        return 0;
                    }
                });

                fieldNode.then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setFieldValue(ctx, field, BoolArgumentType.getBool(ctx, "value"))));

            } else if (type == int.class) {
                fieldNode.then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(ctx -> setFieldValue(ctx, field, IntegerArgumentType.getInteger(ctx, "value"))));
            } else if (type == float.class) {
                fieldNode.then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> setFieldValue(ctx, field, FloatArgumentType.getFloat(ctx, "value"))));
            } else if (type == double.class) {
                fieldNode.then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(ctx -> setFieldValue(ctx, field, DoubleArgumentType.getDouble(ctx, "value"))));
            }

            baseCommand.then(fieldNode);
        }

        dispatcher.register(baseCommand);
    }

    private static int updateConfig(CommandContext<CommandSourceStack> context) {
//        CommandSourceStack source = context.getSource();

        NativeInterface.applyConfig();
        return 0;
    }

    private static int setFieldValue(CommandContext<CommandSourceStack> context, Field field, Object newValue) {
        try {
            field.set(null, newValue); // 静态字段对象传 null
            sendSuccessMessage(context.getSource(), field.getName(), newValue);
            NativeInterface.applyConfig();
            return 1;
        } catch (IllegalAccessException e) {
            context.getSource().sendFailure(Component.literal("Failed to modify config: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
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
        } else {
            line.append(Component.literal(String.valueOf(value))
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }

        line.append("\n");
        return line;
    }

    private static int checkConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        var message = Component.literal("Accelerated Recoiling")
                .withStyle(ChatFormatting.AQUA);

        message.append(Component.literal("\n--------------------\n")
                .withStyle(ChatFormatting.DARK_GRAY));

        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) continue;

            field.setAccessible(true);
            try {
                Object value = field.get(null);
                message.append(buildConfigLine(field.getName(), value));
            } catch (IllegalAccessException e) {
            }
        }

        message.append(Component.literal("--------------------")
                .withStyle(ChatFormatting.DARK_GRAY));

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        File targetFile = new File("acceleratedRecoiling.json");

        JsonObject jsonObject = new JsonObject();

        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;

            field.setAccessible(true);
            try {
                Object value   = field.get(null);
                String jsonKey = field.getName();
                SerializedName serializedName = field.getAnnotation(SerializedName.class);
                if      (serializedName != null)         jsonKey = serializedName.value();
                if      (value instanceof Boolean bool)  jsonObject.addProperty(jsonKey, bool);
                else if (value instanceof Number number) jsonObject.addProperty(jsonKey, number);
                else if (value instanceof String string) jsonObject.addProperty(jsonKey, string);

            } catch (IllegalAccessException _) {
            }
        }

        try (FileWriter writer = new FileWriter(targetFile)) {
            GSON.toJson(jsonObject, writer);

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
}
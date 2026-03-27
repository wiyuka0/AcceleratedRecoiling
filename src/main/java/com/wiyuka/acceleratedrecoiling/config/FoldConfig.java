package com.wiyuka.acceleratedrecoiling.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import org.slf4j.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
public class FoldConfig {

    // 你的静态配置项
    public static final boolean debugDensity = false;
    public static boolean enableEntityCollision = true;
    public static boolean enableEntityGetterOptimization = true;
    public static int maxCollision = 32;
    public static int gridSize = 1;
    public static int densityWindow = 4;
    public static int densityThreshold = 16;
    public static int maxThreads = 1;
    private static final File CONFIG_FILE = new File("acceleratedRecoiling.json");

    public static void loadConfig() {
        Logger logger = AcceleratedRecoiling.LOGGER;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject defaultConfigJson = new JsonObject();
        defaultConfigJson.addProperty("enableEntityCollision", true);
        defaultConfigJson.addProperty("enableEntityGetterOptimization", true);
        defaultConfigJson.addProperty("maxCollision", 32);
        defaultConfigJson.addProperty("gridSize", 1);
        defaultConfigJson.addProperty("densityWindow", 4);
        defaultConfigJson.addProperty("densityThreshold", 16);
        defaultConfigJson.addProperty("maxThreads", maxThreads);

        String defaultConfigStr = gson.toJson(defaultConfigJson);

        if (!CONFIG_FILE.exists()) {
            try {
                if (CONFIG_FILE.createNewFile()) {
                    Files.writeString(CONFIG_FILE.toPath(), defaultConfigStr);
                }
            } catch (IOException e) {
                logger.error("Cannot create config file", e);
            }
        }

        String configFileContent;
        try {
            configFileContent = Files.readString(CONFIG_FILE.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to read config, reason: {}. Using default.", e.getMessage());
            configFileContent = defaultConfigStr;
        }

        try {
            JsonObject configJson = JsonParser.parseString(configFileContent).getAsJsonObject();
            applyJson(configJson);
        } catch (Exception e) {
            logger.warn("Config broken: {}. Overwriting.", e.getMessage());
            try {
                Files.writeString(CONFIG_FILE.toPath(), defaultConfigStr);
            } catch (IOException ignored) {}
            applyJson(JsonParser.parseString(defaultConfigStr).getAsJsonObject());
        }

        logger.info("Configuration loaded successfully");
    }
    private static void applyJson(JsonObject configJson) {
        if (configJson.has("enableEntityCollision")) enableEntityCollision = configJson.get("enableEntityCollision").getAsBoolean();
        if (configJson.has("enableEntityGetterOptimization")) enableEntityGetterOptimization = configJson.get("enableEntityGetterOptimization").getAsBoolean();
        if (configJson.has("maxCollision")) maxCollision = configJson.get("maxCollision").getAsInt();
        if (configJson.has("gridSize")) gridSize = configJson.get("gridSize").getAsInt();
        if (configJson.has("densityWindow")) densityWindow = configJson.get("densityWindow").getAsInt();
        if (configJson.has("densityThreshold")) densityThreshold = configJson.get("densityThreshold").getAsInt();
        if (configJson.has("maxThreads")) maxThreads = configJson.get("maxThreads").getAsInt();
    }
}
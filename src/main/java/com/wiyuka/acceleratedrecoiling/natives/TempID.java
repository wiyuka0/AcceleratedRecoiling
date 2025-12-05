package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.api.ICustomBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;

import java.util.Arrays;

public class TempID {

    private static Entity[] frameSnapshot = new Entity[10000];
    public static int currentIndex = 0;

    public static void tickStart() {
        if (currentIndex > 0) {
            Arrays.fill(frameSnapshot, 0, currentIndex, null);
        }
        currentIndex = 0;
    }

    public static void addEntity(Entity e) {
        if (currentIndex >= frameSnapshot.length) {
            resize();
        }
        int tempId = currentIndex++;
        frameSnapshot[tempId] = e;
         ((ICustomBB)e).setNativeId(tempId);
    }

    public static Entity getEntity(int id) {
        if (id < 0 || id >= currentIndex) return null;
        return frameSnapshot[id];
    }
    public static int getId(Entity e) {
        return ((ICustomBB)e).getNativeId();
    }

    private static void resize() {
        // 扩容 1.5 倍
        int newSize = frameSnapshot.length + (frameSnapshot.length >> 1);
        frameSnapshot = Arrays.copyOf(frameSnapshot, newSize);
    }
}
package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class CollisionMapTemp {
    private static final ConcurrentHashMap<UUID, CopyOnWriteArraySet<UUID>> collisionMap = new ConcurrentHashMap<>();

    public static void addKey(UUID key, CopyOnWriteArraySet<UUID> collisionMap) {
        CollisionMapTemp.collisionMap.put(key, collisionMap);
    }

    public static Set<UUID> get(UUID key) {
        return collisionMap.get(key);
    }

    public static void putCollision(UUID key, UUID value) {
        Set<UUID> collisionSet = collisionMap.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        collisionSet.add(value);
    }

    public static void clear() {
        collisionMap.clear();
    }

    public static List<Entity> replace1(Entity entity, Level instance) {
        Set<UUID> entities = CollisionMapTemp.get(entity.getUUID());
        if(entities == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        for (UUID uuid : entities) {
            Entity entity1 = instance.getEntity(uuid);
            if (entity1 == null) continue;
            result.add(entity1);
        }
        return result;
    }
}



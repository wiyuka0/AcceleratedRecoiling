package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class CollisionMapTemp {
    private static final ConcurrentHashMap<Integer, CopyOnWriteArraySet<Integer>> collisionMap = new ConcurrentHashMap<>();

    public static void addKey(Integer key, CopyOnWriteArraySet<Integer> collisionMap) {
        CollisionMapTemp.collisionMap.put(key, collisionMap);
    }

    public static Set<Integer> get(Integer key) {
        return collisionMap.get(key);
    }

    public static void putCollision(Integer key, Integer value) {
        Set<Integer> collisionSet = collisionMap.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        collisionSet.add(value);
    }

    public static void clear() {
        collisionMap.clear();
    }

    public static List<Entity> replace1(Entity entity, Level instance) {
        Set<Integer> entities = CollisionMapTemp.get(entity.getId());
        if(entities == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        for (Integer id : entities) {
            Entity entity1 = instance.getEntity(id);
            if (entity1 == null) continue;
            result.add(entity1);
        }
        return result;
    }
}



package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.*;

public class CollisionMapData {
    private static final HashMap<UUID, ArrayList<UUID>> collisionMap = new HashMap<>();
    private static final HashMap<UUID, ArrayList<UUID>> collisionMapInverse = new HashMap<>();

    public static ArrayList<UUID> get(UUID key) {
        return collisionMap.get(key);
    }
    public static ArrayList<UUID> getInverse(UUID key) {
        return collisionMapInverse.get(key);
    }

    public static ArrayList<UUID> getBidirectional(UUID key) {
        var positiveList = collisionMap.get(key);
        var negativeList = collisionMapInverse.get(key);
        if (positiveList == null) return negativeList;
        if (negativeList == null) return positiveList;
        var total = new ArrayList<UUID>();
        total.addAll(positiveList);
        total.addAll(negativeList);
        return total;
    }

    public static void putCollision(UUID key, UUID value) {
        ArrayList<UUID> collisionSet = collisionMap.computeIfAbsent(key, k -> new ArrayList<>());
        collisionSet.add(value);
        ArrayList<UUID> collisionSetInverse = collisionMapInverse.computeIfAbsent(value, k -> new ArrayList<>());
        collisionSetInverse.add(key);
    }

    public static void clear() {
        collisionMap.clear();
        collisionMapInverse.clear();
    }

    public static List<Entity> replace1(Entity entity, Level instance, boolean bidirectional) {
        ArrayList<UUID> entities;
        if(bidirectional) entities = CollisionMapData.getBidirectional(entity.getUUID());
        else entities = CollisionMapData.get(entity.getUUID());
        if(entities == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        for (UUID uuid : (ArrayList<UUID>) entities.clone()) {
            Entity entity1 = instance.getEntity(uuid);
            if (entity1 == null) continue;
            result.add(entity1);
        }
        return result;
    }
}



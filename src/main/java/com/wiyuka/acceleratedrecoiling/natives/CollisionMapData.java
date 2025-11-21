package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.*;

public class CollisionMapData {
    private static final HashMap<Integer, ArrayList<Integer>> collisionMap = new HashMap<>();
    private static final HashMap<Integer, ArrayList<Integer>> collisionMapInverse = new HashMap<>();

    public static ArrayList<Integer> get(Integer key) {
        return collisionMap.get(key);
    }
    public static ArrayList<Integer> getInverse(Integer key) {
        return collisionMapInverse.get(key);
    }

    public static ArrayList<Integer> getBidirectional(Integer key) {
        var positiveList = collisionMap.get(key);
        var negativeList = collisionMapInverse.get(key);
        if (positiveList == null) return negativeList;
        if (negativeList == null) return positiveList;
        var total = new ArrayList<Integer>();
        total.addAll(positiveList);
        total.addAll(negativeList);
        return total;
    }

    public static void putCollision(Integer key, Integer value) {
        ArrayList<Integer> collisionSet = collisionMap.computeIfAbsent(key, k -> new ArrayList<>());
        collisionSet.add(value);
        ArrayList<Integer> collisionSetInverse = collisionMapInverse.computeIfAbsent(value, k -> new ArrayList<>());
        collisionSetInverse.add(key);
    }

    public static void clear() {
        collisionMap.clear();
        collisionMapInverse.clear();
    }

    public static List<Entity> replace1(Entity entity, Level instance, boolean bidirectional) {
        ArrayList<Integer> entities;
        if(bidirectional) entities = CollisionMapData.getBidirectional(entity.getId());
        else entities = CollisionMapData.get(entity.getId());
        if(entities == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        for (Integer entityId : (ArrayList<Integer>) entities.clone()) {
            Entity entity1 = instance.getEntity(entityId);
            if (entity1 == null) continue;
            result.add(entity1);
        }
        return result;
    }
}



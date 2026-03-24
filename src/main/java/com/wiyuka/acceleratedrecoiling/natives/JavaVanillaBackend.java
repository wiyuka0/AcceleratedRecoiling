package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Predicate;

public class JavaVanillaBackend implements INativeBackend {
    private static boolean isSelected = false;

    static class SAPContext {
        List<Entity> sortedEntities = new ArrayList<>();
        Map<Integer, Integer> indicesMap = new HashMap<>();
    }
    static SAPContext sapContext;

    public static void tick(EntityTickList entities) {
        if(isSelected) return;
        sapContext = new SAPContext();

        entities.forEach(entity -> {
            sapContext.sortedEntities.add(entity);
        });

        sapContext.sortedEntities.sort(Comparator.comparing(Entity::getX));

        Map<Integer, Integer> tempIdToIndexMap = new HashMap<>();
        for (int i = 0; i < sapContext.sortedEntities.size(); i++) {
            Entity entity = sapContext.sortedEntities.get(i);
            int tempId = TempID.getId(entity);
            tempIdToIndexMap.put(tempId, i);
        }

        sapContext.indicesMap = tempIdToIndexMap;
    }
    public static void clear() {
        sapContext = null;
    }

    public static List<Entity> getPushableEntities(Entity targetEntity, AABB boundingBox) {
        List<Entity> result = new ArrayList<>();

        if (sapContext == null || sapContext.indicesMap == null) return result;

        Integer index = sapContext.indicesMap.get(TempID.getId(targetEntity));
        if (index == null) return result;

        List<Entity> list = sapContext.sortedEntities;

        double targetMinX = boundingBox.minX;
        double targetMaxX = boundingBox.maxX;

        Predicate<Entity> pushPredicate = EntitySelector.pushableBy(targetEntity);

        for (int i = index + 1; i < list.size(); i++) {
            Entity other = list.get(i);
            var otherBox = other.getBoundingBox();

            if (otherBox.minX > targetMaxX) {
                break;
            }

            if (other != targetEntity && boundingBox.intersects(otherBox) && pushPredicate.test(other)) {
                result.add(other);
            }
        }

        for (int i = index - 1; i >= 0; i--) {
            Entity other = list.get(i);
            var otherBox = other.getBoundingBox();

            if (otherBox.maxX < targetMinX) break;

            if (other != targetEntity && boundingBox.intersects(otherBox) && pushPredicate.test(other)) {
                result.add(other);
            }
        }

        // may useless
        /*
        for(EnderDragonPart enderDragonPart : this.dragonParts()) {
            if (enderDragonPart != targetEntity
                && enderDragonPart.parentMob != targetEntity
                && pushPredicate.test(enderDragonPart)
                && boundingBox.intersects(enderDragonPart.getBoundingBox())) {

                result.add(enderDragonPart);
            }
        }
        */

        return result;
    }

    private static void insertionSortAndUpdateMap(SAPContext context) {
        List<Entity> list = context.sortedEntities;
        Map<Integer, Integer> map = context.indicesMap;
        for (int i = 1; i < list.size(); i++) {
            Entity currentEntity = list.get(i);
            int currentId = TempID.getId(currentEntity);
            double currentX = currentEntity.getX();
            int j = i - 1;
            while (j >= 0 && list.get(j).getX() > currentX) {
                Entity entityToShift = list.get(j);

                list.set(j + 1, entityToShift);

                map.put(TempID.getId(entityToShift), j + 1);

                j--;
            }
            list.set(j + 1, currentEntity);

            map.put(currentId, j + 1);
        }
    }

    public static boolean isSelected() {
        return isSelected;
    }

    @Override
    public void initialize() {
        isSelected = true;
    }

    @Override
    public void applyConfig() {

    }

    @Override
    public void destroy() {
        isSelected = false;
    }

    @Override
    public String getName() {
        return "Java Vanilla";
    }

    @Override
    public PushResult push(double[] locations, double[] aabb, int[] resultSizeOut) {
        return null;
    }
}

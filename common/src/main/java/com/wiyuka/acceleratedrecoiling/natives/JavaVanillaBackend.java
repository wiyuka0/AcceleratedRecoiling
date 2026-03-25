package com.wiyuka.acceleratedrecoiling.natives;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Predicate;

public class JavaVanillaBackend implements INativeBackend {
    private static volatile boolean isSelected = false;

    static class SAPContext {
        List<Entity> sortedEntities = new ArrayList<>();
        Map<Integer, Integer> indicesMap = new HashMap<>();

        void clear() {
            sortedEntities.clear();
            indicesMap.clear();
        }
    }

    private static final ThreadLocal<SAPContext> sapContextThreadLocal = ThreadLocal.withInitial(SAPContext::new);

    public static void tick(EntityTickList entities) {
        if (!isSelected) return;
        SAPContext context = sapContextThreadLocal.get();
        context.clear();

        entities.forEach(entity -> {
            context.sortedEntities.add(entity);
        });

        context.sortedEntities.sort(Comparator.comparing(Entity::getX));

        for (int i = 0; i < context.sortedEntities.size(); i++) {
            Entity entity = context.sortedEntities.get(i);
            int tempId = TempID.getId(entity);
            context.indicesMap.put(tempId, i);
        }
    }

    public static void clear() {
        sapContextThreadLocal.remove();
    }

    public static List<Entity> getPushableEntities(Entity targetEntity, AABB boundingBox) {
        List<Entity> result = new ArrayList<>();

        if (!isSelected) return result;

        SAPContext context = sapContextThreadLocal.get();
        if (context.sortedEntities.isEmpty() || context.indicesMap.isEmpty()) return result;

        Integer index = context.indicesMap.get(TempID.getId(targetEntity));
        if (index == null) return result;

        List<Entity> list = context.sortedEntities;

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
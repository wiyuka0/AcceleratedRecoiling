package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.api.ICustomBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashSet;
import java.util.List;

public class ParallelAABB {

    static boolean isInitialized = false;

    static class EntityData {
        LivingEntity entity;
        int count;
        EntityData(LivingEntity entity, int count) {
            this.entity = entity;
            this.count = count;
        }

        void setCount(int count) {
            this.count = count;
        }
    }

    public static void handleEntityPush(final List<Entity> livingEntities, double inflate) {

        CollisionMapData.clear();


        double[] aabb = new double[livingEntities.size() * 6];
        double[] locations = new double[livingEntities.size() * 3];

        int index = 0;
        for (Entity entity : livingEntities) {
            ICustomBB customBB = (ICustomBB) entity;
            customBB.extractionBoundingBox(aabb, index * 6, inflate);
            customBB.extractionPosition(locations, index * 3);
            index++;
        }

        int[] resultCounts = new int[1];

        MemorySegment result = nativePush(locations, aabb, resultCounts);

        if (result == null) return;


        for (int i = 0; i < resultCounts[0]; i++) {
//            int e1Index = result[i * 2];
//            int e2Index = result[i * 2 + 1];
            int e1Index = result.getAtIndex(ValueLayout.JAVA_INT, i * 2);
            int e2Index = result.getAtIndex(ValueLayout.JAVA_INT, i * 2 + 1);
            if (e1Index >= livingEntities.size() || e2Index >= livingEntities.size()) continue;

            Entity e1 = livingEntities.get(e1Index);
            Entity e2 = livingEntities.get(e2Index);

//            if(!e1.getBoundingBox().inflate(inflate).intersects(e2.getBoundingBox().inflate(inflate))) continue;

//            CollisionMapData.putCollision(e1.getUUID(), e2.getUUID());
            LivingEntity livingEntity;
            Entity entity;

            if(e1 instanceof LivingEntity) {
                livingEntity = (LivingEntity) e1;
                entity =  e2;
            } else if(e2 instanceof LivingEntity) {
                livingEntity = (LivingEntity) e2;
                entity = e1;
            } else continue;

//            CollisionMapData.putCollision(livingEntity.getId(), entity.getId());
            if(EntitySelector.pushableBy(livingEntity).test(entity))
                CollisionMapData.putCollision(TempID.getId(livingEntity), TempID.getId(entity));
//            e1.doPush(e2);
//            e2.doPush(e1);

//            entityCollisionMap.computeIfAbsent(e1.getUUID().toString(), k -> new EntityData(e1, 0)).count++;
//            entityCollisionMap.computeIfAbsent(e2.getUUID().toString(), k -> new EntityData(e2, 0)).count++;
        }

//        entityCollisionMap.forEach((id, data) -> {
//            Entity entity = data.entity;
//            if (entity.level() instanceof ServerLevel serverLevel) {
//                int maxCollisionLimit = serverLevel.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
//                if (entity instanceof LivingEntity living && data.count >= maxCollisionLimit && maxCollisionLimit >= 0) {
//                    living.hurt(living.damageSources().cramming(), 6.0F);
//                }
//            }
//        });
    }

    public static MemorySegment nativePush(double[] positions, double[] aabbs, int[] resultSizeOut) {
        if(!isInitialized) {
            NativeInterface.initialize();
            isInitialized = true;
        }
        return NativeInterface.push(positions, aabbs, resultSizeOut);
    }
}


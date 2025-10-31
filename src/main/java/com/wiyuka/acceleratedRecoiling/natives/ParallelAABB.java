package com.wiyuka.acceleratedRecoiling.natives;

import com.wiyuka.acceleratedRecoiling.api.ICustomBB;
import com.wiyuka.acceleratedRecoiling.mixin.EntityMixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParallelAABB {

    public static boolean useFold = true;
    static boolean isInitialized = false;

    static class  EntityData {
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

    public static void handleEntityPush(final List<LivingEntity> livingEntities) {
        Map<String, EntityData> entityCollisionMap = new HashMap<>();

        double[] aabb = new double[livingEntities.size() * 6];
        double[] locations = new double[livingEntities.size() * 3];

        int index = 0;
        for (LivingEntity entity : livingEntities) {
            ICustomBB customBB = (ICustomBB) entity;
            customBB.extractionBoundingBox(aabb, index * 6);
            customBB.extractionPosition(locations, index * 3);
            index++;
        }

        int[] resultCounts = new int[1];

        int[] result = nativePush(locations, aabb, resultCounts);
        if (result == null || result.length % 2 != 0) return;

        for (int i = 0; i * 2 + 1 < result.length && i < resultCounts[0]; i++) {
            int e1Index = result[i * 2];
            int e2Index = result[i * 2 + 1];
            if (e1Index >= livingEntities.size() || e2Index >= livingEntities.size()) continue;

            LivingEntity e1 = livingEntities.get(e1Index);
            LivingEntity e2 = livingEntities.get(e2Index);

            if(!e1.getBoundingBox().intersects(e2.getBoundingBox())) continue;

            e1.push(e2);

            entityCollisionMap.computeIfAbsent(e1.getUUID().toString(), k -> new EntityData(e1, 0)).count++;
            entityCollisionMap.computeIfAbsent(e2.getUUID().toString(), k -> new EntityData(e2, 0)).count++;
        }

        entityCollisionMap.forEach((id, data) -> {
            Entity entity = data.entity;
            if (entity.level() instanceof ServerLevel serverLevel) {
                int maxCollisionLimit = serverLevel.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                if (entity instanceof LivingEntity living && data.count >= maxCollisionLimit) {
                    living.hurt(living.damageSources().cramming(), 6.0F);
                }
            }
        });
    }

    public static int[] nativePush(double[] positions, double[] aabbs, int[] resultSizeOut) {
        if(!isInitialized) {
            NativeInterface.initialize();
            isInitialized = true;
        }
        return NativeInterface.push(positions, aabbs, resultSizeOut);
    }
}


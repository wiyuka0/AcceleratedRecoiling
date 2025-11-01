package com.wiyuka.acceleratedrecoiling.mixin;

import com.wiyuka.acceleratedrecoiling.natives.ParallelAABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    /**
     * 重定向 ServerLevel.tick() 方法中对 entityTickList.forEach() 的调用
     */
    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V", // 目标方法
            at = @At(
                    value = "INVOKE", // 拦截类型：方法调用
                    // 目标方法签名: void net.minecraft.world.level.entity.EntityTickList.forEach(Consumer<Entity>)
                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void onTickEntities(EntityTickList entityTickList, Consumer<Entity> originalConsumer) {
        List<LivingEntity> livingEntities = new ArrayList<>();
        List<Player> playerEntities = new ArrayList<>();

        Consumer<Entity> ourConsumer = entity -> {
            originalConsumer.accept(entity);

            if (!entity.isRemoved()) {
                if (entity instanceof Player) {
                    playerEntities.add((Player) entity);
                } else if (entity instanceof LivingEntity) {
                    livingEntities.add((LivingEntity) entity);
                }
            }
        };

        entityTickList.forEach(ourConsumer);

        if (ParallelAABB.useFold) {
            ParallelAABB.handleEntityPush(livingEntities);
        }
    }
}
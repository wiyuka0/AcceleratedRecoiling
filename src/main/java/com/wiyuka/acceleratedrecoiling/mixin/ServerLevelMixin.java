package com.wiyuka.acceleratedrecoiling.mixin;

import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.ParallelAABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Shadow @Final private EntityTickList entityTickList;

    /**
     * 重定向 ServerLevel.tick() 方法中对 entityTickList.forEach() 的调用
     */

    @Inject(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At("HEAD")
    )
    private void tick(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        List<LivingEntity> livingEntities = new ArrayList<>();
        List<Player> playerEntities = new ArrayList<>();
        this.entityTickList.forEach( entity -> {
            if (!entity.isRemoved()) {
                if (entity instanceof Player) {
                    playerEntities.add((Player) entity);
                } else if (entity instanceof LivingEntity) {
                    livingEntities.add((LivingEntity) entity);
                }
            }
        });
        if (FoldConfig.enableEntityCollision) {
            ParallelAABB.handleEntityPush(livingEntities, 1.0E-3);
        }
    }

//    @Redirect(
//            method = "tick(Ljava/util/function/BooleanSupplier;)V", // 目标方法
//            at = @At(
//                    value = "INVOKE", // 拦截类型：方法调用
//                    // 目标方法签名: void net.minecraft.world.level.entity.EntityTickList.forEach(Consumer<Entity>)
//                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
//            )
//    )
//    private void onTickEntities(EntityTickList entityTickList, Consumer<Entity> originalConsumer) {
//        List<LivingEntity> livingEntities = new ArrayList<>();
//        List<Player> playerEntities = new ArrayList<>();
//
//        Consumer<Entity> ourConsumer = entity -> {
//            originalConsumer.accept(entity);
//        };
//
//        entityTickList.forEach(ourConsumer);
//
//
//    }
}
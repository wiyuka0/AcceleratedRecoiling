package com.wiyuka.acceleratedrecoiling.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wiyuka.acceleratedrecoiling.api.ICustomData;
import com.wiyuka.acceleratedrecoiling.api.ICustomData;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import com.wiyuka.acceleratedrecoiling.natives.JavaVanillaBackend;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(value = LivingEntity.class, priority = 1100)
public class LivingEntityMixin {
//    @Inject(
//            method = "pushEntities",
//            at = @At(
//                    "HEAD"
//            ),
//            cancellable = true
//    )
//    private void pushEntities(final CallbackInfo ci) {
//        LivingEntity self = (LivingEntity)(Object)this;
//        if(self.level().isClientSide) return;
//
    ////        ci.cancel();
//        if((FoldConfig.fold) && self.getType() != EntityType.PLAYER) {
//            ci.cancel();
//        }
//    }
    @Unique
    private int lastClimbableCheckTick = -1;
    @Unique
    private boolean cachedClimbableResult = false;


    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void doPushVerify(LivingEntity instance, Entity entity, Operation<Void> original) {
        if(instance.getBoundingBox().intersects(entity.getBoundingBox())) original.call(instance, entity);
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void injectOnClimbableHead(CallbackInfoReturnable<Boolean> cir) {
        if (((LivingEntity)(Object)this).tickCount == this.lastClimbableCheckTick) {
            cir.setReturnValue(this.cachedClimbableResult);
        }
    }
    @Inject(method = "onClimbable", at = @At("RETURN"))
    private void injectOnClimbableReturn(CallbackInfoReturnable<Boolean> cir) {
        this.lastClimbableCheckTick = ((LivingEntity)(Object)this).tickCount;
        this.cachedClimbableResult = cir.getReturnValueZ();
    }

    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<Entity> replace(Level instance, Entity entity, AABB aabb, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
        if (!FoldConfig.enableEntityCollision || entity instanceof Player || entity.level().isClientSide())
            return original.call(instance, entity, aabb, predicate);
        if(JavaVanillaBackend.isSelected()) return JavaVanillaBackend.getPushableEntities(entity,  aabb);

        ICustomData data = (ICustomData) entity;
        if (data.getDensity() < FoldConfig.densityThreshold) return original.call(instance, entity, aabb, predicate);

        List<Entity> rawList = CollisionMapData.getCollisionList(entity, instance);
        List<Entity> filteredList = new ArrayList<>();
        for (Entity e : rawList) {
            if (e != entity && predicate.test(e)) {
                filteredList.add(e);
            }
        }
        return filteredList;
    }

//    @Inject(
//            method = "aiStep",
//            at = @At(
//                    "HEAD"
//            ),
//            cancellable = true
//    )
//    private void aiStep(final CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if(self instanceof Player) return;
//        ci.cancel();
//
//    }
//    @Inject(
//            method = "serverAiStep",
//            at = @At(
//                    "HEAD"
//            ),
//            cancellable = true
//    )
//    private void serverAiStep(final CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//
//        if(self instanceof Player) return;
//        ci.cancel();
//    }
//    @Redirect(
//            method = "aiStep",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/entity/LivingEntity;pushEntities()V"
//            ),
//            cancellable = true
//    )
//    public void pushEntities(LivingEntity livingEntity) {
//        if (!ParallelAABB.useFold || livingEntity instanceof Player) {
//            pushEntities(livingEntity);
//        }
//    }
}

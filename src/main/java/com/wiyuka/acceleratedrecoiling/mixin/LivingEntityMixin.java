package com.wiyuka.acceleratedrecoiling.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(LivingEntity.class)
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

    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"
            )
    )
    private boolean isPassenger(Entity instance, Operation<Boolean> original) {
        if (instance.isPassenger()) {
            return true;
        }
        AABB myBox = ((LivingEntity)(Object)this).getBoundingBox();
        AABB otherBox = instance.getBoundingBox();
        if (!myBox.intersects(otherBox)) return true;
        return false;
    }


    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getPushableEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> replace(Level instance, Entity entity, AABB boundingBox, Operation<List<Entity>> original) {
//        Set<UUID> entities = CollisionMapTemp.get(entity.getUUID());
//        if(entities == null) return Collections.emptyList();
//        List<Entity> result = new ArrayList<>();
//        for (UUID uuid : entities) {
//            Entity entity1 = instance.getEntity(uuid);
//            if (entity1 == null) continue;
//            result.add(entity1);
//        }
//        return result;

//        var source = CollisionMapData.getCollisionList(entity, instance);

        if(FoldConfig.enableEntityCollision && !(entity instanceof Player) && !entity.level().isClientSide)
            return CollisionMapData.getCollisionList(entity, instance);
        else
            return original.call(instance, entity, boundingBox);
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

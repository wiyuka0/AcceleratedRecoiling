package com.wiyuka.acceleratedrecoiling.mixin;


import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapTemp;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;

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



    @Redirect(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getPushableEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> replace(Level instance, Entity entity, AABB boundingBox) {
//        Set<UUID> entities = CollisionMapTemp.get(entity.getUUID());
//        if(entities == null) return Collections.emptyList();
//        List<Entity> result = new ArrayList<>();
//        for (UUID uuid : entities) {
//            Entity entity1 = instance.getEntity(uuid);
//            if (entity1 == null) continue;
//            result.add(entity1);
//        }
//        return result;
        if(FoldConfig.fold)
            return CollisionMapTemp.replace1(entity, instance);
        else
            return instance.getPushableEntities(entity, boundingBox);
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

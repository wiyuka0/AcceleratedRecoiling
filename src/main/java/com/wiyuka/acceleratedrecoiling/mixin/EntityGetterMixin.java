package com.wiyuka.acceleratedrecoiling.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Mixin(value = EntityGetter.class)
public interface EntityGetterMixin {

//    @WrapOperation(
//            method = "getEntityCollisions",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/level/EntityGetter;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
//    )
//    default List<Entity> getEntityCollisions(EntityGetter instance, Entity entity, AABB aabb, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
//        if(FoldConfig.enableEntityGetterOptimization && !(entity instanceof Player) && entity != null)
//            return CollisionMapData.replace1(entity, entity.level()).stream().filter(predicate).collect(Collectors.toList());
//        else
//            return original.call(instance, entity, aabb, predicate);
//    }
}

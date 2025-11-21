package com.wiyuka.acceleratedrecoiling.mixin;


import com.google.common.collect.ImmutableList;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Mixin(value = EntityGetter.class)
public interface EntityGetterMixin {

    @Shadow List<Entity> getEntities(@Nullable Entity p_45934_, AABB p_45935_, Predicate<Entity> predicate);


    @Unique
    default List<Entity> acceleratedRecoiling$getEntitiesA(Entity entity, AABB aabb, Predicate<Entity> predicate) {
        if(FoldConfig.enableEntityGetterOptimization && !(entity instanceof Player) && entity != null)
            return CollisionMapData.replace1(entity, entity.level(), true).stream().filter(predicate).collect(Collectors.toList());
        else
            return this.getEntities(entity, aabb.inflate(1.0E-7), predicate);
    }
    /**
     * @author wiyuka
     * @reason Forge voxel collision support
     */
    @Overwrite
    default List<VoxelShape> getEntityCollisions(Entity p_186451_, AABB p_186452_) {
        if (p_186452_.getSize() < 1.0E-7) {
            return List.of();
//            cir.setReturnValue(List.of());
        } else {
            Predicate var10000;
            if (p_186451_ == null) {
                var10000 = EntitySelector.CAN_BE_COLLIDED_WITH;
            } else {
                var10000 = EntitySelector.NO_SPECTATORS;
                Objects.requireNonNull(p_186451_);
                var10000 = var10000.and(p_20303_ -> p_186451_.canCollideWith((Entity)p_20303_));
            }

            Predicate<Entity> $$2 = var10000;
            List<Entity> $$3 = this.acceleratedRecoiling$getEntitiesA(p_186451_, p_186452_.inflate(1.0E-7), $$2);
            if ($$3.isEmpty()) {
                return List.of();
//                cir.setReturnValue(List.of());
            } else {
                ImmutableList.Builder<VoxelShape> $$4 = ImmutableList.builderWithExpectedSize($$3.size());

                for(Entity $$5 : $$3) {
                    $$4.add(Shapes.create($$5.getBoundingBox()));
                }

                return $$4.build();
//                cir.setReturnValue($$4.build());
            }
        }
    }
}

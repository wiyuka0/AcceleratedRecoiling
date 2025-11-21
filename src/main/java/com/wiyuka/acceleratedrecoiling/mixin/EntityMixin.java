package com.wiyuka.acceleratedrecoiling.mixin;

import com.wiyuka.acceleratedrecoiling.api.ICustomBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements ICustomBB {
    private double bbMinX = 0.0;
    private double bbMinY = 0.0;
    private double bbMinZ = 0.0;
    private double bbMaxX = 0.0;
    private double bbMaxY = 0.0;
    private double bbMaxZ = 0.0;


    @Override
    public final void extractionBoundingBox(double[] doubleArray, int offset) {
        doubleArray[offset + 0] = (double) this.bbMinX;
        doubleArray[offset + 1] = (double) this.bbMinY;
        doubleArray[offset + 2] = (double) this.bbMinZ;
        doubleArray[offset + 3] = (double) this.bbMaxX;
        doubleArray[offset + 4] = (double) this.bbMaxY;
        doubleArray[offset + 5] = (double) this.bbMaxZ;
    }

    @Shadow
    private Vec3 position;


    @Override
    public final void extractionPosition(double[] doubleArray, int offset) {
        doubleArray[offset + 0] = (double) this.position.x;
        doubleArray[offset + 1] = (double) this.position.y;
        doubleArray[offset + 2] = (double) this.position.z;
    }

    @Inject(
            method = "setBoundingBox(Lnet/minecraft/world/phys/AABB;)V",
            at = @At("RETURN")
    )
    private void onSetBoundingBox(AABB bb, CallbackInfo ci) {
        this.bbMinX = bb.minX;
        this.bbMinY = bb.minY;
        this.bbMinZ = bb.minZ;
        this.bbMaxX = bb.maxX;
        this.bbMaxY = bb.maxY;
        this.bbMaxZ = bb.maxZ;
    }
}
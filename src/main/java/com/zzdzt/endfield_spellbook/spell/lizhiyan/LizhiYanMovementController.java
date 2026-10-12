package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Low-level damped movement and gradual facing for LizhiYan.
 *
 * <p>The entity remains the owner of position, velocity, and rotation state.
 * This controller only computes and applies movement primitives; attack phases
 * and their timing remain in {@link LizhiYanEntity}.</p>
 */
final class LizhiYanMovementController {
    private final LizhiYanEntity entity;

    LizhiYanMovementController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    void flyToward(Vec3 destination, double steering, double maxSpeed) {
        flyToward(destination, steering, maxSpeed, null, 0.28);
    }

    void flyToward(Vec3 destination, double steering, double maxSpeed, @Nullable Vec3 facing) {
        flyToward(destination, steering, maxSpeed, facing, 0.28);
    }

    void flyToward(Vec3 destination, double steering, double maxSpeed,
                   @Nullable Vec3 facing, double turnRate) {
        Vec3 offset = destination.subtract(entity.position());
        Vec3 desired = offset.lengthSqr() < 1e-6 ? Vec3.ZERO
                : offset.normalize().scale(Math.min(maxSpeed, Math.max(0.08, offset.length())));
        Vec3 motion = entity.getDeltaMovement().scale(1.0 - steering).add(desired.scale(steering));
        if (motion.length() > maxSpeed) motion = motion.normalize().scale(maxSpeed);
        entity.setDeltaMovement(motion);
        entity.setPos(entity.position().add(motion));
        entity.hasImpulse = true;
        Vec3 face = facing != null ? facing : motion;
        if (face.lengthSqr() > 1e-5) faceDirection(face, turnRate);
    }

    /** Gradually rotate the sword tip toward the requested direction. */
    void faceDirection(Vec3 requested, double turnRate) {
        Vec3 current = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot());
        Vec3 target = requested.lengthSqr() < 1e-6 ? current : requested.normalize();
        Vec3 blended = current.scale(1.0 - turnRate).add(target.scale(turnRate));
        if (blended.lengthSqr() < 1e-6) blended = target;
        blended = blended.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-blended.x, blended.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(blended.y, blended.horizontalDistance()));
        entity.applyMovementRotation(yaw, pitch);
    }

    boolean closeTo(Vec3 point, double radius) {
        return point.distanceToSqr(entity.position()) < radius * radius;
    }
}

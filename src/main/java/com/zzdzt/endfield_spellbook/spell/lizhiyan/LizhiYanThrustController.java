package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the physical sub-state machine for LizhiYan's stab:
 * windup pullback, target-tracking dash, and momentum-driven follow-through.
 *
 * <p>The entity retains the top-level attack phase, synced flags, damage source,
 * and the transition back to formation / coordinated blast.</p>
 */
final class LizhiYanThrustController {
    private enum Stage { WINDUP, DASH, THROUGH }

    private final LizhiYanEntity entity;
    private boolean active;
    private boolean hasDealtDamage;
    private Vec3 targetPoint = Vec3.ZERO;
    @Nullable private Vec3 direction;
    @Nullable private Stage stage;
    @Nullable private Vec3 waypoint;
    private int timeout;

    LizhiYanThrustController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    boolean isActive() {
        return active;
    }

    void resetDamageState() {
        hasDealtDamage = false;
    }

    void start() {
        LivingEntity target = entity.currentThrustTarget();
        if (target == null) {
            entity.returnToIdleAfterMissingThrustTarget();
            return;
        }

        active = true;
        hasDealtDamage = false;
        entity.beginThrustPhase();
        Vec3 origin = entity.position();
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 toTarget = targetCenter.subtract(origin);
        direction = toTarget.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : toTarget.normalize();
        targetPoint = targetCenter;
        stage = Stage.WINDUP;
        waypoint = origin.subtract(direction.scale(0.6));
        timeout = 0;
        entity.setThrustingSynced(true);
        entity.playThrustStartSound();
    }

    void tick() {
        if (!active || stage == null) {
            finish();
            return;
        }

        switch (stage) {
            case WINDUP -> {
                if (waypoint == null) {
                    finish();
                    return;
                }
                entity.flyToward(waypoint, 0.30, 0.50, direction, 1.0);
                if (entity.closeTo(waypoint, 0.15)) {
                    stage = Stage.DASH;
                    timeout = 0;
                }
            }
            case DASH -> {
                LivingEntity target = entity.currentThrustTarget();
                Vec3 aimPoint = target != null && target.isAlive()
                    ? target.position().add(0, target.getBbHeight() * 0.55, 0)
                    : targetPoint;
                Vec3 previous = entity.position();
                Vec3 facing = aimPoint.subtract(entity.position());
                if (facing.lengthSqr() < 1.0e-6) facing = direction != null ? direction : new Vec3(0, 0, 1);
                entity.flyToward(aimPoint, 0.25, 1.18, facing);

                if (!hasDealtDamage && target != null && target.isAlive()) {
                    var box = target.getBoundingBox().inflate(0.3);
                    if (box.contains(entity.position()) || box.clip(previous, entity.position()).isPresent()) {
                        entity.applyStabDamage(target);
                        hasDealtDamage = true;
                    }
                }

                boolean reached = aimPoint.distanceToSqr(entity.position()) < 0.4 * 0.4;
                if (hasDealtDamage || reached) {
                    Vec3 travel = entity.getDeltaMovement();
                    if (travel.lengthSqr() < 1.0e-6) {
                        travel = direction != null ? direction : new Vec3(0, 0, 1);
                    }
                    waypoint = entity.position().add(travel.normalize().scale(1.5)).add(0, 0.2, 0);
                    stage = Stage.THROUGH;
                } else if (++timeout > 30) {
                    finish();
                }
            }
            case THROUGH -> {
                if (waypoint == null) {
                    finish();
                    return;
                }
                entity.flyToward(waypoint, 0.14, 0.96);
                if (entity.closeTo(waypoint, 0.2)) finish();
            }
        }
    }

    void forceFinish() {
        finish();
    }

    private void finish() {
        if (!active) return;
        active = false;
        stage = null;
        waypoint = null;
        hasDealtDamage = false;
        entity.setThrustingSynced(false);
        entity.onThrustFinished();
    }
}

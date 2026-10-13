package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Owns spatial behavior: idle formation, return movement, coordinated-blast positioning,
 * spin orbit, and aim smoothing. Attack timing remains in LizhiYanAttackController.
 */
final class LizhiYanFormationController {
    private static final Vec3[] FORMATION_OFFSETS = {
        new Vec3(-2.0, 1.2, 0.0),
        new Vec3(2.0, 1.2, 0.0),
        new Vec3(0.0, 1.8, -1.5)
    };

    private static final Vec3[] DEFAULT_AIM = {
        new Vec3(-0.5, 0, 0.8).normalize(),
        new Vec3(0.5, 0, 0.8).normalize(),
        new Vec3(0.0, 0.1, 1.0).normalize()
    };

    private static final float FORMATION_LERP = 0.12f;
    private static final float IDLE_SWAY_AMP = 0.03f;
    private static final float IDLE_SWAY_FREQ = 0.02f;
    private static final double AIM_DIR_THRESHOLD_SQ = 0.001;
    private static final int POSITION_UPDATE_INTERVAL = 2;
    private static final int RETURN_TICKS = 10;
    private static final float SPIN_RADIUS = 1.5f;
    private static final float SPIN_HEIGHT = 1.2f;
    private static final float SPIN_CENTER_DISTANCE = 2.0f;

    private final LizhiYanEntity entity;

    private Vec3 lastOwnerPos = Vec3.ZERO;
    private Vec3 lastAimDir = Vec3.ZERO;
    private float lastOwnerYaw;
    private double lastCosYaw = 1.0;
    private double lastSinYaw;
    private int lastFormationComputeTick = -1;
    private int returnTick;

    LizhiYanFormationController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    static Vec3 defaultAim(int slot) {
        return DEFAULT_AIM[slot];
    }

    void initializePosition(LivingEntity owner) {
        Vec3 offset = FORMATION_OFFSETS[entity.getSlotIndex()];
        float ownerYaw = owner.getYRot();
        double yawRad = ownerYaw * 0.0174533f;
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rx = offset.x * cos - offset.z * sin;
        double rz = offset.x * sin + offset.z * cos;
        entity.setPos(owner.getX() + rx, owner.getY() + offset.y, owner.getZ() + rz);
    }

    boolean isReturning() {
        return returnTick > 0;
    }

    void beginReturn() {
        returnTick = RETURN_TICKS;
    }

    void cancelReturn() {
        returnTick = 0;
    }

    void updateReturnToFormation(LivingEntity owner) {
        refreshOwnerTransform(owner);

        Vec3 offset = FORMATION_OFFSETS[entity.getSlotIndex()];
        double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
        double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
        Vec3 target = owner.position().add(rx, offset.y, rz);
        double swayY = Math.sin(entity.tickCount * IDLE_SWAY_FREQ + entity.getSlotIndex() * 2.1)
                * IDLE_SWAY_AMP;
        target = target.add(0, swayY, 0);

        entity.flyToward(target, 0.22, 0.82, target.subtract(entity.position()).normalize());
        if (entity.closeTo(target, 0.25) || --returnTick <= 0) {
            returnTick = 0;
        }
    }

    void updateFormationPosition(LivingEntity owner) {
        refreshOwnerTransform(owner);

        Vec3 offset = FORMATION_OFFSETS[entity.getSlotIndex()];
        Vec3 target;
        if (entity.tickCount % POSITION_UPDATE_INTERVAL == 0 || lastFormationComputeTick < 0) {
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            target = owner.position().add(rx, offset.y, rz);
            double swayY = Math.sin(entity.tickCount * IDLE_SWAY_FREQ + entity.getSlotIndex() * 2.1)
                    * IDLE_SWAY_AMP;
            target = target.add(0, swayY, 0);
            lastFormationComputeTick = entity.tickCount;
        } else {
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            target = owner.position().add(rx, offset.y, rz);
            double swayY = Math.sin(entity.tickCount * IDLE_SWAY_FREQ + entity.getSlotIndex() * 2.1)
                    * IDLE_SWAY_AMP;
            target = target.add(0, swayY, 0);
        }

        Vec3 next = entity.position().lerp(target, FORMATION_LERP);
        entity.setPos(next.x, next.y, next.z);
        entity.markMovementImpulse();
        updateAimDirection(owner);
    }

    private void refreshOwnerTransform(LivingEntity owner) {
        float ownerYaw = owner.getYRot();
        if (ownerYaw != lastOwnerYaw || lastOwnerPos.distanceToSqr(owner.position()) > 0.01) {
            lastOwnerYaw = ownerYaw;
            lastOwnerPos = owner.position();
            double yawRad = ownerYaw * 0.0174533f;
            lastCosYaw = Math.cos(yawRad);
            lastSinYaw = Math.sin(yawRad);
        }
    }

    void updateAimDirection(LivingEntity owner) {
        Entity target = entity.targetingController().getCurrentTarget();
        if (target == null) {
            target = entity.targetingController().getPendingTarget();
        }

        Vec3 aimDir;
        if (target != null && target.isAlive()) {
            Vec3 targetCenter = target.position().add(0, target.getBbHeight() / 2, 0);
            aimDir = targetCenter.subtract(entity.position()).normalize();
        } else {
            Vec3 defaultAim = DEFAULT_AIM[entity.getSlotIndex()];
            double rx = defaultAim.x * lastCosYaw - defaultAim.z * lastSinYaw;
            double rz = defaultAim.x * lastSinYaw + defaultAim.z * lastCosYaw;
            aimDir = new Vec3(rx, defaultAim.y, rz).normalize();
        }

        if (aimDir.distanceToSqr(lastAimDir) < AIM_DIR_THRESHOLD_SQ) {
            entity.refreshAimRotationHistory();
            return;
        }
        lastAimDir = aimDir;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-aimDir.x, aimDir.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(aimDir.y,
                Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z)));
        entity.applyAimRotation(
                lerpAngle(entity.currentAimYaw(), targetYaw, 0.15f),
                lerpAngle(entity.currentAimPitch(), targetPitch, 0.15f));
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return from + diff * t;
    }

    void updateSpin(LivingEntity owner, int phaseTick, int spinTicks) {
        double progress = phaseTick / (double) spinTicks;
        double baseAngle = entity.getSlotIndex() * (2 * Math.PI / 3);
        double spinAngle = baseAngle - progress * 2 * Math.PI;
        float yawRad = owner.getYRot() * 0.0174533f;

        double behindX = Math.sin(yawRad) * SPIN_CENTER_DISTANCE;
        double behindZ = -Math.cos(yawRad) * SPIN_CENTER_DISTANCE;
        double centerX = owner.getX() + behindX;
        double centerY = owner.getY() + SPIN_HEIGHT;
        double centerZ = owner.getZ() + behindZ;
        double offsetRight = Math.cos(spinAngle) * SPIN_RADIUS;
        double offsetUp = Math.sin(spinAngle) * SPIN_RADIUS;
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        Vec3 target = new Vec3(
                centerX + offsetRight * rightX,
                centerY + offsetUp,
                centerZ + offsetRight * rightZ);
        Vec3 next = entity.position().lerp(target, 0.2f);
        entity.setPos(next.x, next.y, next.z);
        entity.markMovementImpulse();

        Vec3 center = new Vec3(centerX, centerY, centerZ);
        Vec3 fromCenter = next.subtract(center).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-fromCenter.x, fromCenter.z));
        float pitch = (float) -Math.toDegrees(Math.asin(fromCenter.y));
        entity.applyMovementRotation(yaw, pitch);
    }

    void updateBlastReadyPosition(LivingEntity owner) {
        Vec3 lockedPos = entity.getBlastReadyPos();
        if (lockedPos == null) {
            lockedPos = computeBlastPosition(owner, entity.getSlotIndex());
            entity.setBlastReadyPos(lockedPos);
        }
        entity.flyToward(lockedPos, 0.30, 1.0, aimFacing(owner));
    }

    void holdBlastPosition(LivingEntity owner) {
        Vec3 lockedPos = entity.getBlastReadyPos();
        if (lockedPos == null) return;
        entity.flyToward(lockedPos, 0.25, 0.6, aimFacing(owner));
    }

    private Vec3 computeBlastPosition(LivingEntity owner, int slot) {
        float yawRad = owner.getYRot() * 0.0174533f;
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        Vec3 center = owner.position()
                .add(sin * 2.5, 1.4, cos * 2.5)
                .add(-cos * 1.6, 0, -sin * 1.6);
        double angle = slot * 2.0 * Math.PI / 3.0 + Math.PI / 2.0;
        Vec3 right = new Vec3(cos, 0, sin);
        return center.add(right.scale(Math.cos(angle) * 0.7))
                .add(0, Math.sin(angle) * 0.7, 0);
    }

    boolean swordReadyAndAtPos(LivingEntity owner) {
        LizhiYanEntity.Phase phase = entity.getAttackPhaseState();
        if (phase == LizhiYanEntity.Phase.BLAST
                || phase == LizhiYanEntity.Phase.BLAST_DELAY
                || phase == LizhiYanEntity.Phase.SPIN) {
            return true;
        }
        if (phase != LizhiYanEntity.Phase.BLAST_READY) return false;

        Vec3 lockedPos = entity.getBlastReadyPos();
        Vec3 target = lockedPos != null
                ? lockedPos
                : computeBlastPosition(owner, entity.getSlotIndex());
        return entity.closeTo(target, 0.6);
    }

    private Vec3 aimFacing(LivingEntity owner) {
        Entity target = entity.targetingController().getCurrentTarget();
        if (target != null && target.isAlive()) {
            return target.position().add(0, target.getBbHeight() / 2, 0)
                    .subtract(entity.position()).normalize();
        }
        float yawRad = owner.getYRot() * 0.0174533f;
        return new Vec3((float) -Math.sin(yawRad), 0, (float) -Math.cos(yawRad));
    }
}

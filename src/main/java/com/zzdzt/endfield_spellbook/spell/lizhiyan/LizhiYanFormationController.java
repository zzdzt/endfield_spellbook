package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the formation geometry and positioning behavior used by coordinated blasts.
 * Attack phase timing and sibling-sword coordination remain in their existing owners.
 */
final class LizhiYanFormationController {
    private final LizhiYanEntity entity;

    LizhiYanFormationController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    /** Move toward the firing position locked for the current coordinated-blast cycle. */
    void updateBlastReadyPosition(LivingEntity owner) {
        Vec3 lockedPos = entity.getBlastReadyPos();
        if (lockedPos == null) {
            lockedPos = computeBlastPosition(owner, entity.getSlotIndex());
            entity.setBlastReadyPos(lockedPos);
        }
        entity.flyToward(lockedPos, 0.30, 1.0, aimFacing(owner));
    }

    /** Hold the locked firing position through blast and post-blast delay. */
    void holdBlastPosition(LivingEntity owner) {
        Vec3 lockedPos = entity.getBlastReadyPos();
        if (lockedPos == null) return;
        entity.flyToward(lockedPos, 0.25, 0.6, aimFacing(owner));
    }

    /**
     * Firing position behind and slightly left of the owner, with a vertical triangular
     * distribution so the beams originate behind rather than through the owner.
     */
    Vec3 computeBlastPosition(LivingEntity owner, int slot) {
        float yawRad = owner.getYRot() * 0.0174533f;
        double sin = Math.sin(yawRad), cos = Math.cos(yawRad);
        Vec3 center = owner.position()
                .add(sin * 2.5, 1.4, cos * 2.5)
                .add(-cos * 1.6, 0, -sin * 1.6);
        double angle = slot * 2.0 * Math.PI / 3.0 + Math.PI / 2.0;
        Vec3 right = new Vec3(cos, 0, sin);
        return center.add(right.scale(Math.cos(angle) * 0.7))
                .add(0, Math.sin(angle) * 0.7, 0);
    }

    /** Whether this sword has reached its locked firing position and is ready to volley. */
    boolean swordReadyAndAtPos(LivingEntity owner) {
        LizhiYanEntity.Phase phase = entity.getAttackPhaseState();
        // Once firing or entering the spin sequence, do not block the remaining swords.
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

    /** Sword-tip facing: track a live target, otherwise face forward relative to the owner. */
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

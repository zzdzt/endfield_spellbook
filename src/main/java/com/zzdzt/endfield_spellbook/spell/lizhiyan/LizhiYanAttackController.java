package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Orchestrates LizhiYan's top-level attack phase machine.
 *
 * <p>Phase storage and synced presentation remain on the entity; this class
 * owns the transition rules and tick counters. Target selection, movement,
 * and the thrust sub-state machine remain delegated to their own controllers.</p>
 */
final class LizhiYanAttackController {
    private final LizhiYanEntity entity;
    private LizhiYanEntity.Phase phase = LizhiYanEntity.Phase.IDLE;
    private int phaseTick;
    private int attackIndex;

    LizhiYanAttackController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    LizhiYanEntity.Phase phase() { return phase; }
    int phaseTick() { return phaseTick; }
    int attackIndex() { return attackIndex; }
    void setPhase(LizhiYanEntity.Phase value) { phase = value; }
    void setPhaseTick(int value) { phaseTick = value; }
    void setAttackIndex(int value) { attackIndex = Math.max(0, value); }
    void incrementAttackIndex() { attackIndex++; }

    void tick(LivingEntity owner) {
        switch (phase) {
            case IDLE:
                if (entity.targetingController().getPendingTarget() != null
                        && entity.targetingController().getPendingTarget().isAlive()) {
                    entity.targetingController().setCurrentTarget(
                        entity.targetingController().getPendingTarget());
                    entity.targetingController().setPendingTarget(null);
                    attackIndex = 0;
                    entity.targetingController().resetLostSightTick();
                }

                if (entity.targetingController().getCurrentTarget() != null
                        && entity.targetingController().getCurrentTarget().isAlive()) {
                    phase = LizhiYanEntity.Phase.CHARGE;
                    phaseTick = 0;
                    entity.blastLockedDirection = null;
                    entity.resetThrustDamageState();
                }
                break;

            case CHARGE:
                phaseTick++;
                int chargeThreshold = LizhiYanEntity.CHARGE_TICKS_STAB;

                if (entity.targetingController().getCurrentTarget() != null) {
                    entity.updateAimDirection(owner);
                }

                if (attackIndex >= 2
                        && entity.targetingController().getCurrentTarget() != null) {
                    Vec3 tc = entity.targetingController().getCurrentTarget().position()
                        .add(0, entity.targetingController().getCurrentTarget().getBbHeight() / 2, 0);
                    entity.blastLockedDirection = tc.subtract(entity.position()).normalize();
                }

                float chargeProgress = phaseTick / (float) chargeThreshold;
                float pulse = 1.0f + (float) Math.sin(chargeProgress * Math.PI) * 0.25f;
                entity.setChargeScaleSynced(pulse);

                if (phaseTick >= chargeThreshold) {
                    entity.setChargeScaleSynced(1.0f);
                    if (attackIndex < 2) entity.startStab();
                    else entity.startBlastReady();
                }
                break;

            case STAB:
                // The thrust controller advances its sub-stages; retain the hard timeout.
                entity.phaseTick++;
                if (entity.phaseTick >= LizhiYanEntity.STAB_MAX_TICKS) {
                    entity.forceFinishThrust();
                }
                break;

            case BLAST_READY:
                // Wait until all swords have reached their locked firing positions,
                // then hold briefly for coordinated charge before firing.
                entity.phaseTick++;
                if (entity.allAtBlastPositions(owner)) entity.blastReadyHold++;
                else entity.blastReadyHold = 0;
                if (entity.blastReadyHold >= LizhiYanEntity.BLAST_CHARGE_TICKS
                        || entity.phaseTick >= LizhiYanEntity.BLAST_READY_TIMEOUT) {
                    entity.startBlast(owner);
                    entity.blastReadyHold = 0;
                }
                break;

            case BLAST:
                phase = LizhiYanEntity.Phase.BLAST_DELAY;
                phaseTick = 0;
                break;

            case BLAST_DELAY:
                entity.phaseTick++;
                if (entity.phaseTick >= LizhiYanEntity.BLAST_TO_SPIN_DELAY) {
                    entity.phase = LizhiYanEntity.Phase.SPIN;
                    entity.phaseTick = 0;
                }
                break;

            case SPIN:
                entity.phaseTick++;
                if (entity.phaseTick >= entity.getSpinTicksForController()) {
                    attackIndex = 0;
                    entity.phase = LizhiYanEntity.Phase.COOLDOWN;
                    entity.phaseTick = 0;
                    // Release the firing-position lock and charge counter for the next cycle.
                    entity.blastReadyPos = null;
                    entity.blastReadyHold = 0;
                }
                break;

            case COOLDOWN:
                entity.phaseTick++;
                int cooldown = attackIndex == 0
                    ? LizhiYanEntity.COOLDOWN_TICKS_BLAST
                    : LizhiYanEntity.COOLDOWN_TICKS_STAB;

                if (entity.phaseTick >= cooldown) {
                    if (entity.targetingController().getPendingTarget() != null
                            && entity.targetingController().getPendingTarget().isAlive()) {
                        entity.targetingController().setCurrentTarget(
                            entity.targetingController().getPendingTarget());
                        entity.targetingController().setPendingTarget(null);
                        entity.attackIndex = 0;
                        entity.phase = LizhiYanEntity.Phase.CHARGE;
                        entity.phaseTick = 0;
                        entity.blastLockedDirection = null;
                        entity.resetThrustDamageState();
                    } else if (entity.targetingController().getCurrentTarget() != null
                            && entity.targetingController().getCurrentTarget().isAlive()) {
                        entity.phase = LizhiYanEntity.Phase.CHARGE;
                        entity.phaseTick = 0;
                        entity.blastLockedDirection = null;
                        entity.resetThrustDamageState();
                    } else {
                        entity.phase = LizhiYanEntity.Phase.IDLE;
                        entity.phaseTick = 0;
                        entity.attackIndex = 0;
                    }
                }
                break;
        }
    }
}

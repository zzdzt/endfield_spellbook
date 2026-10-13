package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Orchestrates LizhiYan's top-level attack phase machine.
 *
 * <p>This class owns the top-level phase, phase timer, and attack index.
 * Target selection, movement, and the thrust sub-state machine remain delegated
 * to their own controllers; the entity retains lifecycle and synced presentation.</p>
 */
final class LizhiYanAttackController {
    private final LizhiYanEntity entity;
    private LizhiYanEntity.Phase phase = LizhiYanEntity.Phase.IDLE;
    private int phaseTick;
    private int attackIndex;
    @Nullable private Vec3 blastLockedDirection;
    @Nullable private Vec3 blastReadyPos;
    private int blastReadyHold;

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
    @Nullable Vec3 blastLockedDirection() { return blastLockedDirection; }
    void setBlastLockedDirection(@Nullable Vec3 value) { blastLockedDirection = value; }
    @Nullable Vec3 blastReadyPos() { return blastReadyPos; }
    void setBlastReadyPos(@Nullable Vec3 value) { blastReadyPos = value; }
    int blastReadyHold() { return blastReadyHold; }
    void incrementBlastReadyHold() { blastReadyHold++; }
    void resetBlastReadyHold() { blastReadyHold = 0; }

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
                    blastLockedDirection = null;
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
                    blastLockedDirection = tc.subtract(entity.position()).normalize();
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
                phaseTick++;
                if (phaseTick >= LizhiYanEntity.STAB_MAX_TICKS) {
                    entity.forceFinishThrust();
                }
                break;

            case BLAST_READY:
                // Wait until all swords have reached their locked firing positions,
                // then hold briefly for coordinated charge before firing.
                phaseTick++;
                if (entity.allAtBlastPositions(owner)) blastReadyHold++;
                else blastReadyHold = 0;
                if (blastReadyHold >= LizhiYanEntity.BLAST_CHARGE_TICKS
                        || phaseTick >= LizhiYanEntity.BLAST_READY_TIMEOUT) {
                    entity.startBlast(owner);
                    blastReadyHold = 0;
                }
                break;

            case BLAST:
                phase = LizhiYanEntity.Phase.BLAST_DELAY;
                phaseTick = 0;
                break;

            case BLAST_DELAY:
                phaseTick++;
                if (phaseTick >= LizhiYanEntity.BLAST_TO_SPIN_DELAY) {
                    phase = LizhiYanEntity.Phase.SPIN;
                    phaseTick = 0;
                }
                break;

            case SPIN:
                phaseTick++;
                if (phaseTick >= entity.getSpinTicksForController()) {
                    attackIndex = 0;
                    phase = LizhiYanEntity.Phase.COOLDOWN;
                    phaseTick = 0;
                    // Release the firing-position lock and charge counter for the next cycle.
                    blastReadyPos = null;
                    blastReadyHold = 0;
                }
                break;

            case COOLDOWN:
                phaseTick++;
                int cooldown = attackIndex == 0
                    ? LizhiYanEntity.COOLDOWN_TICKS_BLAST
                    : LizhiYanEntity.COOLDOWN_TICKS_STAB;

                if (phaseTick >= cooldown) {
                    if (entity.targetingController().getPendingTarget() != null
                            && entity.targetingController().getPendingTarget().isAlive()) {
                        entity.targetingController().setCurrentTarget(
                            entity.targetingController().getPendingTarget());
                        entity.targetingController().setPendingTarget(null);
                        attackIndex = 0;
                        phase = LizhiYanEntity.Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        entity.resetThrustDamageState();
                    } else if (entity.targetingController().getCurrentTarget() != null
                            && entity.targetingController().getCurrentTarget().isAlive()) {
                        phase = LizhiYanEntity.Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        entity.resetThrustDamageState();
                    } else {
                        phase = LizhiYanEntity.Phase.IDLE;
                        phaseTick = 0;
                        attackIndex = 0;
                    }
                }
                break;
        }
    }
}

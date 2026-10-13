package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.core.particles.ParticleTypes;

/** Client-side visual effects for the LizhiYan summon. */
final class LizhiYanPresentationController {
    private final LizhiYanEntity entity;

    LizhiYanPresentationController(LizhiYanEntity entity) {
        this.entity = entity;
    }

    void tickClient(boolean firstTick, LizhiYanEntity.Phase phase, float chargeScale, boolean thrusting) {
        if (firstTick) {
            spawnSummonParticles();
        }

        if (phase == LizhiYanEntity.Phase.CHARGE && entity.tickCount % 5 == 0) {
            float angle = entity.tickCount * 0.3f + entity.getSlotIndex() * 2.1f;
            for (int i = 0; i < 2; i++) {
                float a = angle + (float) (Math.PI * i);
                double px = entity.getX() + Math.cos(a) * chargeScale * 0.4;
                double pz = entity.getZ() + Math.sin(a) * chargeScale * 0.4;
                entity.level().addParticle(ParticleTypes.ENCHANT, px, entity.getY(), pz, 0, 0.03, 0);
            }
        }

        if (phase == LizhiYanEntity.Phase.SPIN && entity.tickCount % 3 == 0) {
            entity.level().addParticle(ParticleTypes.ENCHANT,
                    entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 0.3,
                    entity.getY() + (entity.getRandom().nextDouble() - 0.5) * 0.3,
                    entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 0.3,
                    (entity.getRandom().nextDouble() - 0.5) * 0.1,
                    (entity.getRandom().nextDouble() - 0.5) * 0.1,
                    (entity.getRandom().nextDouble() - 0.5) * 0.1);
        }

        if (thrusting && entity.tickCount % 3 == 0) {
            entity.level().addParticle(ParticleTypes.CRIT,
                    entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 0.2,
                    entity.getY() + (entity.getRandom().nextDouble() - 0.5) * 0.2,
                    entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 0.2,
                    0, 0, 0);
        }

        if (entity.tickCount % 10 == 0) {
            entity.level().addParticle(ParticleTypes.ENCHANT,
                    entity.getX(), entity.getY(), entity.getZ(), 0, 0.03, 0);
        }
    }

    void spawnSummonParticles() {
        for (int i = 0; i < 10; i++) {
            entity.level().addParticle(ParticleTypes.END_ROD,
                    entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 0.4,
                    entity.getY() + (entity.getRandom().nextDouble() - 0.5) * 0.4,
                    entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 0.4,
                    0, 0.05, 0);
        }
    }

    void spawnRemovalParticles() {
        for (int i = 0; i < 12; i++) {
            entity.level().addParticle(ParticleTypes.END_ROD,
                    entity.getX() + (entity.getRandom().nextDouble() - 0.5),
                    entity.getY() + (entity.getRandom().nextDouble() - 0.5),
                    entity.getZ() + (entity.getRandom().nextDouble() - 0.5),
                    (entity.getRandom().nextDouble() - 0.5) * 0.1,
                    0.05,
                    (entity.getRandom().nextDouble() - 0.5) * 0.1);
        }
    }
}

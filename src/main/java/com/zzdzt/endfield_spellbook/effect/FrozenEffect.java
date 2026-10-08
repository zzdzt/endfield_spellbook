package com.zzdzt.endfield_spellbook.effect;

import com.zzdzt.endfield_spellbook.element.FrozenWindowStore;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 冻结的视觉载体（纯标记效果）。
 *
 * <p>冻结窗口的计数业务在 {@code FrozenWindowStore}（persistentData）——MobEffect 的
 * duration 是时间语义，表达不了"剩余次数"；本效果只承担原版 UI 图标 + swirl 粒子，
 * 并按剩余次数持续在目标身上漂浮冰晶（次数 → 粒子量），窗口用尽时由
 * {@code ElementReactionHandler} 移除本效果。
 */
public class FrozenEffect extends MagicMobEffect {

    public FrozenEffect() {
        super(MobEffectCategory.HARMFUL, 0xBFE7FF); // 寒冷冰蓝
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 10 == 0; // 每 0.5 秒一帧冰晶
    }

    @Override
    public void applyEffectTick(LivingEntity target, int amplifier) {
        if (target.level().isClientSide
            || !(target.level() instanceof ServerLevel serverLevel)) return;

        // 剩余次数 → 冰晶量（每次受击消耗一粒，视觉即计数）
        int hits = FrozenWindowStore.getHitsRemaining(target);
        int count = Math.max(1, Math.min(6, hits));
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) count) * Math.PI * 2 + target.tickCount * 0.05;
            double radius = target.getBbWidth() * 0.6;
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                target.getX() + Math.cos(angle) * radius,
                target.getY() + target.getBbHeight() * (0.4 + 0.1 * (i % 3)),
                target.getZ() + Math.sin(angle) * radius,
                1, 0, 0.01, 0, 0.002);
        }
    }
}

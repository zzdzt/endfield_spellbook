package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.effect.CombustionEffect;
import com.zzdzt.endfield_spellbook.effect.ErodedEffect;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import javax.annotation.Nullable;

/** 元素反应结算服务：负责同元素法术爆发、异元素异常、初始伤害及反应反馈。 */
public final class ElementReactionService {
    private ElementReactionService() {
    }

    public static final float BURST_BASE = 6.0f;
    public static final float INITIAL_BASE = 8.0f;
    public static final float FROZEN_BONUS_PER_STAGE = 0.10f;
    public static final long FROZEN_WINDOW_TIMEOUT_TICKS = 15 * 20;

    /** 法术爆发：BURST_BASE × SP × 层数，元素色迸发。 */
    static void burst(ServerLevel level, LivingEntity attacker, LivingEntity target,
                              EndfieldElement element, int stacks, float spellPower) {
        float damage = BURST_BASE * spellPower * stacks;
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, damage, elementDamageSource(level, element, attacker));
        spawnBurstParticles(level, target, element);
    }

    /** 法术异常：初始伤害 + 按触发元素分派异常效果。 */
    static void triggerReaction(ServerLevel level, LivingEntity attacker, LivingEntity target,
                                        EndfieldElement trigger, int stage, float spellPower) {
        // 反应初始伤害（四异常共用）
        float initial = INITIAL_BASE * spellPower * stage;
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, initial, elementDamageSource(level, trigger, attacker));

        switch (trigger) {
            case HEAT -> {
                // 燃烧：DoT effect（阶段 = amplifier + 1），DoT 伤害归属施加者
                CombustionEffect.storeCaster(target, attacker, spellPower);
                target.addEffect(new MobEffectInstance(EffectRegistry.COMBUSTION.get(),
                    CombustionEffect.EFFECT_DURATION, stage - 1));
            }
            case ELECTRIC -> {
                // 导电：法术易伤（阶段 = amplifier + 1）
                target.addEffect(new MobEffectInstance(EffectRegistry.ARTS_VULNERABLE.get(),
                    20 * (12 + 6 * (stage - 1)), stage - 1));
            }
            case NATURE -> {
                // 腐蚀：降疗 + 降伤（阶段 = amplifier + 1）
                target.addEffect(new MobEffectInstance(EffectRegistry.ERODED.get(),
                    ErodedEffect.EFFECT_DURATION, stage - 1));
            }
            case CRYO -> {
                // 冻结：下（阶段）次伤害提高（阶段 1~4 → 1~4 次）+ 视觉标记效果
                FrozenWindowStore.set(target,
                    stage,
                    FROZEN_BONUS_PER_STAGE * stage,
                    level.getGameTime() + FROZEN_WINDOW_TIMEOUT_TICKS);
                target.addEffect(new MobEffectInstance(EffectRegistry.FROZEN_MARK.get(),
                    (int) FROZEN_WINDOW_TIMEOUT_TICKS, stage - 1));
            }
        }
        spawnBurstParticles(level, target, trigger);
    }

    /**
     * 元素伤害源（带攻击者归属）：伤害类型用元素对应的 ISS 学派 DamageType，
     * causingEntity = 施加者——保证击杀 credit、仇恨、死讯归属与战斗状态刷新正确。
     */
    private static net.minecraft.world.damagesource.DamageSource elementDamageSource(
        ServerLevel level, EndfieldElement element, @Nullable LivingEntity attacker) {
        var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getHolderOrThrow(element.damageTypeKey());
        return new net.minecraft.world.damagesource.DamageSource(holder, null, attacker);
    }

    /** 元素色迸发（复用破晦阵 launch 命中参数：Blastwave 环 1.25f + Spark 火花 25 颗 0.2f）。 */
    private static void spawnBurstParticles(ServerLevel level, LivingEntity target, EndfieldElement element) {
        float[] c = element.particleColor();
        Vector3f col = new Vector3f(c[0], c[1], c[2]);
        Vec3 feet = target.position().add(0, 0.1, 0);
        MagicManager.spawnParticles(level,
            new BlastwaveParticleOptions(col, 1.25f),
            feet.x, feet.y, feet.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level,
            new SparkParticleOptions(col),
            feet.x, feet.y, feet.z, 25, 0.2f, 0.2f, 0.2f, 0.25f, true);
    }
}

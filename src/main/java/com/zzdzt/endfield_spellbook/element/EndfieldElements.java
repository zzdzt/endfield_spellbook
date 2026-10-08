package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.effect.ArtsVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.CombustionEffect;
import com.zzdzt.endfield_spellbook.effect.ErodedEffect;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityTags;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 元素附着机制公开 API 门面（终末地「法术附着/法术爆发/法术异常」的 MC 本地化）。
 *
 * <p><b>资源模型</b>：附着是敌人身上的资源——只有我们的法术通过本门面挂附着（生产者）
 * 或查询/消耗附着（消费者）；铁魔法原版与原版伤害不经过此系统。
 *
 * <p><b>apply 自动规则</b>：
 * <ul>
 *   <li>无/过期附着 → 挂 1 层（单时间戳刷新制）</li>
 *   <li>同元素叠加 → 层数 +1（≤4 封顶）并触发<b>法术爆发</b>（BURST_BASE × 法术强度 × 层数）</li>
 *   <li>异元素施加 → 消耗全部层数触发<b>法术异常</b>（阶段 = 消耗层数）并清空附着</li>
 * </ul>
 *
 * <p><b>四异常</b>（均附带初始伤害，官方名）：
 * 燃烧（灼热，DoT）/ 导电（电磁，法术易伤）/ 腐蚀（自然，降疗降伤）/ 冻结（寒冷，下 X 次伤害提高）。
 *
 * <p><b>防御</b>：单向机制——ServerPlayer 永不成为附着目标；REACTION_IMMUNE tag 实体免疫。
 */
public final class EndfieldElements {

    private EndfieldElements() {
    }

    // ==================== 数值占位（全部待定，进游戏后统一调） ====================

    /** 附着层数上限（原作 4 层封顶）。 */
    public static final int MAX_STACKS = 4;
    /** 附着持续时间（tick，单时间戳刷新制）——待定：8~15s（决定"叠层玩法"稀有度）。 */
    public static final long INFLICTION_DURATION_TICKS = 8 * 20;
    /** 法术爆发 = BURST_BASE × 施加者法术强度 × 当前层数。 */
    public static final float BURST_BASE = 6.0f;
    /** 反应初始伤害 = INITIAL_BASE × 施加者法术强度 × 阶段（四异常共用）。 */
    public static final float INITIAL_BASE = 8.0f;
    /** 冻结窗口：增伤幅度 = FROZEN_BONUS_PER_STAGE × 阶段（占位 10%/阶段）。 */
    public static final float FROZEN_BONUS_PER_STAGE = 0.10f;
    /** 冻结窗口：次数 = 阶段（阶段 1/2/3/4 → 下 1/2/3/4 次伤害提高）。 */
    /** 冻结窗口兜底时限（次数用尽前防长期空挂）。 */
    public static final long FROZEN_WINDOW_TIMEOUT_TICKS = 15 * 20;

    // ==================== 生产者 ====================

    /**
     * 挂附着入口（生产者法术在命中时调用）。
     * 自动处理：新附着 / 同元素叠层+爆发 / 异元素反应，并同步脚下弧环视觉。
     */
    public static void apply(LivingEntity attacker, LivingEntity target, EndfieldElement element) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (target instanceof ServerPlayer || !target.isAlive() || target.isRemoved()) return;
        // 免疫 tag：异常效果属于强控制，Boss 免疫
        if (target.getType().is(EntityTags.REACTION_IMMUNE)) return;

        long now = level.getGameTime();
        float spellPower = (float) attacker.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        if (!ElementInflictionStore.isValid(target, now)) {
            // 无附着 / 已过期 → 新附着 1 层
            ElementInflictionStore.set(target, element, 1, now + INFLICTION_DURATION_TICKS);
        } else if (ElementInflictionStore.getElement(target) == element) {
            // 同元素 → 叠层 + 法术爆发
            int stacks = Math.min(MAX_STACKS, ElementInflictionStore.getStacks(target) + 1);
            ElementInflictionStore.set(target, element, stacks, now + INFLICTION_DURATION_TICKS);
            burst(level, attacker, target, element, stacks, spellPower);
        } else {
            // 异元素 → 消耗全部层数触发异常（阶段 = 旧层数），清空附着
            int stage = ElementInflictionStore.getStacks(target);
            ElementInflictionStore.clear(target);
            triggerReaction(level, attacker, target, element, stage, spellPower);
        }
        refreshRing(target, now);
    }

    // ==================== 消费者 ====================

    /** 目标当前是否持有指定元素的有效附着。 */
    public static boolean hasElement(LivingEntity target, EndfieldElement element) {
        if (!(target.level() instanceof ServerLevel level)) return false;
        return ElementInflictionStore.isValid(target, level.getGameTime())
            && ElementInflictionStore.getElement(target) == element;
    }

    /** 读取当前有效附着层数；无附着返回 -1（消费前可先 {@link #hasElement} 校验元素）。 */
    public static int peekStacks(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return -1;
        long now = level.getGameTime();
        return ElementInflictionStore.isValid(target, now) ? ElementInflictionStore.getStacks(target) : -1;
    }

    /**
     * 消耗全部附着（引爆类预留），返回消耗层数（阶段）；无附着返回 0。
     * 清空附着与弧环。
     */
    public static int consume(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return 0;
        long now = level.getGameTime();
        if (!ElementInflictionStore.isValid(target, now)) return 0;
        int stacks = ElementInflictionStore.getStacks(target);
        ElementInflictionStore.clear(target);
        refreshRing(target, now);
        return stacks;
    }

    /** 匹配元素才消耗（元素不符原样保留），返回消耗层数。 */
    public static int consume(LivingEntity target, EndfieldElement element) {
        if (!(target.level() instanceof ServerLevel level)) return 0;
        if (!ElementInflictionStore.isValid(target, level.getGameTime())
            || ElementInflictionStore.getElement(target) != element) return 0;
        return consume(target);
    }

    /** 读取导电异常等级（惊霆诀 WP4 使用；无导电返回 0）。 */
    public static int getElectrificationStage(LivingEntity target) {
        return ArtsVulnerableEffect.getStage(target, EffectRegistry.ARTS_VULNERABLE.get());
    }

    // ==================== 内部：爆发 / 反应 / 视觉 ====================

    /** 法术爆发：BURST_BASE × SP × 层数，元素色迸发。 */
    private static void burst(ServerLevel level, LivingEntity attacker, LivingEntity target,
                              EndfieldElement element, int stacks, float spellPower) {
        float damage = BURST_BASE * spellPower * stacks;
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, damage, elementDamageSource(level, element, attacker));
        spawnBurstParticles(level, target, element);
    }

    /** 法术异常：初始伤害 + 按触发元素分派异常效果。 */
    private static void triggerReaction(ServerLevel level, LivingEntity attacker, LivingEntity target,
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

    // ==================== 弧环视觉同步 ====================

    /**
     * 刷新目标脚下的附着弧环：查找同目标旧环丢弃后按当前状态重建
     * （有效附着 → 环寿命 = 附着剩余时间；无附着 → 仅丢弃 = 消散）。
     */
    private static void refreshRing(LivingEntity target, long now) {
        if (!(target.level() instanceof ServerLevel level)) return;
        // 丢弃旧环
        for (ElementRingEntity old : level.getEntitiesOfClass(ElementRingEntity.class,
            target.getBoundingBox().inflate(4),
            ring -> target.getUUID().equals(ring.getTargetId()) && !ring.isRemoved())) {
            old.discard();
        }
        if (!ElementInflictionStore.isValid(target, now)) return;

        var element = ElementInflictionStore.getElement(target);
        int stacks = ElementInflictionStore.getStacks(target);
        float radius = Math.max(0.85f, target.getBbWidth() * 0.8f);
        // 刚刷新过附着 → 环寿命 = 附着时长 + 10 tick 淡出缓冲
        int lifetime = (int) Math.min(Integer.MAX_VALUE, INFLICTION_DURATION_TICKS + 10);
        var ring = new ElementRingEntity(EntityRegistry.ELEMENT_RING.get(), level,
            target.position(), target.getUUID(), element, stacks, radius, lifetime);
        level.addFreshEntity(ring);
    }
}

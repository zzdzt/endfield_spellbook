package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.effect.ArtsVulnerableEffect;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityTags;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;


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
    public static final float BURST_BASE = ElementReactionService.BURST_BASE;
    /** 反应初始伤害 = INITIAL_BASE × 施加者法术强度 × 阶段（四异常共用）。 */
    public static final float INITIAL_BASE = ElementReactionService.INITIAL_BASE;
    /** 冻结窗口：增伤幅度 = FROZEN_BONUS_PER_STAGE × 阶段（占位 10%/阶段）。 */
    public static final float FROZEN_BONUS_PER_STAGE = ElementReactionService.FROZEN_BONUS_PER_STAGE;
    /** 冻结窗口：次数 = 阶段（阶段 1/2/3/4 → 下 1/2/3/4 次伤害提高）。 */
    /** 冻结窗口兜底时限（次数用尽前防长期空挂）。 */
    public static final long FROZEN_WINDOW_TIMEOUT_TICKS = ElementReactionService.FROZEN_WINDOW_TIMEOUT_TICKS;

    // ==================== 生产者 ====================

    /**
     * 挂附着入口（生产者法术在命中时调用）。
     * 自动处理：新附着 / 同元素叠层+爆发 / 异元素反应，并同步脚下弧环视觉。
     */
    public static void apply(LivingEntity attacker, LivingEntity target, EndfieldElement element) {
        applyInternal(attacker, target, element, true);
    }

    /**
     * Shared rules. Listener notification is selected per call, not through global mutable state.
     * Regular nested apply() calls still notify; the delayed focus bonus explicitly opts out.
     */
    private static void applyInternal(LivingEntity attacker, LivingEntity target, EndfieldElement element,
                                      boolean notifyListeners) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (target instanceof ServerPlayer || !target.isAlive() || target.isRemoved()) return;
        if (target.getType().is(EntityTags.REACTION_IMMUNE)) return;

        long now = level.getGameTime();
        float spellPower = (float) attacker.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        if (!ElementInflictionStore.isValid(target, now)) {
            ElementInflictionStore.set(target, element, 1, now + INFLICTION_DURATION_TICKS);
        } else if (ElementInflictionStore.getElement(target) == element) {
            int stacks = Math.min(MAX_STACKS, ElementInflictionStore.getStacks(target) + 1);
            ElementInflictionStore.set(target, element, stacks, now + INFLICTION_DURATION_TICKS);
            ElementReactionService.burst(level, attacker, target, element, stacks, spellPower);
        } else {
            int stage = ElementInflictionStore.getStacks(target);
            ElementInflictionStore.clear(target);
            ElementReactionService.triggerReaction(level, attacker, target, element, stage, spellPower);
        }

        ElementRingController.refreshRing(target, now);
        if (notifyListeners) {
            InflictionHub.notifyApplied(attacker, target, element);
        }
    }

    // ==================== 消费者 ====================

    /**
     * 聚焦补层专用：施加附着但<b>不广播</b>（补层不视为新的"受到附着"，防延迟任务再触发补层连锁）。
     * 叠加/反应规则与 {@link #apply} 完全一致。
     */
    public static void applyNoNotify(LivingEntity attacker, LivingEntity target, EndfieldElement element) {
        applyInternal(attacker, target, element, false);
    }


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
     * 消耗指定元素的单层附着，返回消耗前层数；其他元素或无效附着不变。
     * 保留剩余层数的过期时间，并刷新弧环，供单层强化爆发类技能使用。
     */
    public static int consumeOne(LivingEntity target, EndfieldElement element) {
        if (!(target.level() instanceof ServerLevel level)) return 0;
        long now = level.getGameTime();
        if (!ElementInflictionStore.isValid(target, now)
            || ElementInflictionStore.getElement(target) != element) return 0;

        int before = ElementInflictionStore.getStacks(target);
        if (before <= 1) {
            ElementInflictionStore.clear(target);
        } else {
            ElementInflictionStore.set(target, element, before - 1,
                ElementInflictionStore.getExpireAtGameTime(target));
        }
        ElementRingController.refreshRing(target, now);
        return before;
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
        ElementRingController.refreshRing(target, now);
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

}

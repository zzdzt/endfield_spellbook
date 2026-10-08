package com.zzdzt.endfield_spellbook.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 腐蚀（自然触发的法术异常）：目标造成的伤害降低，且受到的治疗降低。
 *
 * <p>两个比例均随阶段提升（阶段 = amplifier + 1），数值占位待定：
 * 降伤/降疗各 10% × 阶段（1→10% ... 4→40%），持续 15 秒。
 * 乘区在 {@code ElementReactionHandler} 的 LivingHurtEvent / LivingHealEvent 中结算。
 */
public class ErodedEffect extends MagicMobEffect {

    /** 持续时间（tick）：15 秒。 */
    public static final int EFFECT_DURATION = 20 * 15;
    /** 数值占位（待定）：每阶段的降伤/降疗比例。 */
    public static final float REDUCTION_PER_STAGE = 0.10f;

    public ErodedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5AB862); // 自然草绿
    }

    /** 目标造成的伤害降低比例（阶段 1~4 → 0.10/0.20/0.30/0.40）。 */
    public static float getDamageReduction(int amplifier) {
        return REDUCTION_PER_STAGE * (amplifier + 1);
    }

    /** 目标受到的治疗降低比例（同上）。 */
    public static float getHealReduction(int amplifier) {
        return REDUCTION_PER_STAGE * (amplifier + 1);
    }
}

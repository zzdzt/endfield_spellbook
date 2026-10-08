package com.zzdzt.endfield_spellbook.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 导电（电磁触发的法术异常）：目标受到的我们的法术伤害提高。
 *
 * <p>增幅 = 12% + 4% × 阶段（阶段 = amplifier + 1，1→12% ... 4→24%），与原作数值表一致。
 * 纯标记效果，增伤乘区在 {@code ElementReactionHandler} 的 LivingHurtEvent 中结算。
 */
public class ArtsVulnerableEffect extends MagicMobEffect {

    public ArtsVulnerableEffect() {
        super(MobEffectCategory.HARMFUL, 0xD9C24A); // 电磁金紫
    }

    /** 导电易伤倍率（阶段 1~4 → 0.12/0.16/0.20/0.24）。 */
    public static float getAmplification(int amplifier) {
        return 0.08f + 0.04f * (amplifier + 1);
    }

    /** 目标当前导电阶段（无导电返回 0）。惊霆诀（WP4）按此读导电等级。 */
    public static int getStage(LivingEntity target, net.minecraft.world.effect.MobEffect effect) {
        var instance = target.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }
}

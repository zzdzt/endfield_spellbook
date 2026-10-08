package com.zzdzt.endfield_spellbook.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 灼热脆弱：目标受到的灼热伤害提高。
 *
 * <p>增幅 = 20%（占位固定一档）。判定按伤害类型（灼热 = FIRE_MAGIC），
 * 覆盖：我方火学派法术、元素爆发/反应伤害、燃烧 DoT 每跳——与燃烧 DoT 的联动即由此免费获得。
 * 增伤乘区在 {@code ElementReactionHandler} 的 LivingHurtEvent 中结算。
 */
public class HeatVulnerableEffect extends MagicMobEffect {

    /** 灼热易伤增伤（占位：固定 20%）。 */
    public static final float AMPLIFICATION = 0.20f;

    public HeatVulnerableEffect() {
        super(MobEffectCategory.HARMFUL, 0xC81E3C); // 血红
    }
}

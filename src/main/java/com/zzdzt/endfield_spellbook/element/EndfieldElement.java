package com.zzdzt.endfield_spellbook.element;

import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * 终末地四元素（官方命名）。
 *
 * <p>每个元素映射一个 ISS 学派伤害类型，用作反应/爆发伤害的 DamageType 来源
 * （灼热→fire_magic、电磁→lightning_magic、寒冷→ice_magic、自然→nature_magic），
 * 语义对齐且零新资源。
 */
public enum EndfieldElement {
    /** 灼热（原作 Heat）：触发异常 = 燃烧。 */
    HEAT,
    /** 电磁（原作 Electric）：触发异常 = 导电。 */
    ELECTRIC,
    /** 寒冷（原作 Cryo）：触发异常 = 冻结。 */
    CRYO,
    /** 自然（原作 Nature）：触发异常 = 腐蚀。 */
    NATURE;

    /** 粒子/弧环配色（RGB 0~1）：灼热橙红 / 电磁金紫 / 寒冷冰蓝 / 自然草绿。 */
    public float[] particleColor() {
        return switch (this) {
            case HEAT -> new float[]{1.00f, 0.45f, 0.15f};
            case ELECTRIC -> new float[]{0.95f, 0.80f, 0.30f};
            case CRYO -> new float[]{0.55f, 0.80f, 1.00f};
            case NATURE -> new float[]{0.35f, 0.85f, 0.40f};
        };
    }

    /** 反应/爆发伤害所属的 ISS 学派伤害类型。 */
    public ResourceKey<DamageType> damageTypeKey() {
        return switch (this) {
            case HEAT -> ISSDamageTypes.FIRE_MAGIC;
            case ELECTRIC -> ISSDamageTypes.LIGHTNING_MAGIC;
            case CRYO -> ISSDamageTypes.ICE_MAGIC;
            case NATURE -> ISSDamageTypes.NATURE_MAGIC;
        };
    }
}

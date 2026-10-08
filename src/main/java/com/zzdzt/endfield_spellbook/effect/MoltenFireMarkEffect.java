package com.zzdzt.endfield_spellbook.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 熔火标记（视觉载体）：层数（amplifier + 1）自动同步客户端，
 * 原版效果栏图标 + 时间条（长度 ≈ 层数比例）即玩家侧的层数显示。
 *
 * <p>层数业务在 {@code PlayerCharges}（persistentData）；本效果由
 * {@code PlayerCharges} 在层数变化时刷新——MobEffect 自动跨端同步。无自带粒子。
 */
public class MoltenFireMarkEffect extends MagicMobEffect {

    public MoltenFireMarkEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE87326); // 熔火橙
    }
}

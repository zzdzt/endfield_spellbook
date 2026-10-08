package com.zzdzt.endfield_spellbook.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 聚焦（指定研究对象）：目标被「聚焦」的 HUD 标记 debuff，纯显示无 tick 逻辑。
 *
 * <p>由 FocusMarkEntity 施加/移除（时长 = 聚焦剩余寿命 + 缓冲）；补层逻辑
 * 在标记实体与 {@code InflictionHub}，不经过本效果。
 */
public class FocusedEffect extends MagicMobEffect {

    public FocusedEffect() {
        super(MobEffectCategory.HARMFUL, 0xF2CC4D); // 电磁金
    }
}

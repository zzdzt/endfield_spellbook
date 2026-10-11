package com.zzdzt.endfield_spellbook.effect;

import com.zzdzt.endfield_spellbook.element.MoltenFireFeedback;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 熔火标记（视觉载体）：层数业务保存在 PlayerCharges。
 *
 * <p>只在标记接近到期时执行一次续期检查；没有熔火标记的玩家不会参与这段逻辑，
 * 也不需要由全体玩家 Tick 事件轮询标记生命周期。
 */
public class MoltenFireMarkEffect extends MagicMobEffect {

    public MoltenFireMarkEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE87326); // 熔火橙
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 标记只在最后 15 tick 执行一次续期检查。
        return duration <= 15;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            MoltenFireFeedback.maintainMark(entity);
        }
    }
}

package com.zzdzt.endfield_spellbook.effect;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;


/**
 * 风矢穿林专用浮空效果。
 *
 * <p>不同于创造飞行或 setNoGravity 标志，本效果通过重力属性修饰器把实体重力降为零。
 * 持续期间重置坠落距离，避免上升/滞空过程中累计危险的坠落距离。
 */
public class WindArrowFloatEffect extends MagicMobEffect {
    private static final ResourceLocation GRAVITY_MODIFIER_ID =
        new ResourceLocation(EndfieldSpellbook.MOD_ID, "wind_arrow_float_gravity");

    public WindArrowFloatEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x76C65B);
        addAttributeModifier(
            ForgeMod.ENTITY_GRAVITY.get(),
            GRAVITY_MODIFIER_ID,
            -1.0,
            AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.resetFallDistance();
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}

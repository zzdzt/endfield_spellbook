package com.zzdzt.endfield_spellbook.effect;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * 燃烧（灼热触发的法术异常）：持续灼烧 DoT。
 *
 * <p>伤害 = DOT_BASE × 施加时法术强度 × (1 + 0.5 × 阶段) / 秒，持续 10 秒。
 * 阶段（amplifier）= 消耗的灼热附着层数 - 1；施加时法术强度存入目标 persistentData
 * （LiquidNitrogenMarkedEffect 模式），效果移除时清理。
 */
public class CombustionEffect extends MagicMobEffect {

    /** 持续时间（tick）：10 秒。 */
    public static final int EFFECT_DURATION = 20 * 10;
    /** 数值占位（待定）：每秒 DoT 基准。 */
    public static final float DOT_BASE = 3.0f;

    public static final String NBT_POWER = "endfield_spellbook:combustion_power";
    public static final String NBT_CASTER_UUID = "endfield_spellbook:combustion_caster";

    public CombustionEffect() {
        super(MobEffectCategory.HARMFUL, 0xE85A1E); // 灼热橙红
    }

    /** 施加时调用：记录施加者与施加时的法术强度（阶段由 amplifier 承载）。 */
    public static void storeCaster(LivingEntity target, LivingEntity caster, float spellPower) {
        CompoundTag data = target.getPersistentData();
        data.putUUID(NBT_CASTER_UUID, caster.getUUID());
        data.putFloat(NBT_POWER, spellPower);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每秒结算一次 DoT
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity target, int amplifier) {
        if (target.level().isClientSide
            || !(target.level() instanceof ServerLevel serverLevel)) return;

        CompoundTag data = target.getPersistentData();
        float spellPower = data.getFloat(NBT_POWER);
        // 阶段 = amplifier + 1（0 → 1 层灼热附着触发的燃烧）
        float damage = DOT_BASE * spellPower * (1.0f + 0.5f * (amplifier + 1));
        if (damage <= 0) return;

        // 伤害归属：带施加者（击杀 credit/仇恨），施加者已卸载则退化为无主源
        var holder = serverLevel.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getHolderOrThrow(EndfieldElement.HEAT.damageTypeKey());
        LivingEntity caster = data.hasUUID(NBT_CASTER_UUID)
            && serverLevel.getEntity(data.getUUID(NBT_CASTER_UUID)) instanceof LivingEntity living
            ? living : null;
        var damageSource = new net.minecraft.world.damagesource.DamageSource(holder, null, caster);

        // 灼烧伤害绕过普攻无敌帧（DoT 叠加在受击之上）
        target.invulnerableTime = 0;
        target.hurt(damageSource, damage);

        // 火焰粒子（少量橙红火尘上浮）
        var flame = new DustParticleOptions(new Vector3f(1.00f, 0.45f, 0.15f), 0.6f);
        serverLevel.sendParticles(flame,
            target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            3, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.01);
    }

    @Override
    public void onEffectRemoved(LivingEntity target, int amplifier) {
        CompoundTag data = target.getPersistentData();
        data.remove(NBT_POWER);
        data.remove(NBT_CASTER_UUID);
        super.onEffectRemoved(target, amplifier);
    }
}

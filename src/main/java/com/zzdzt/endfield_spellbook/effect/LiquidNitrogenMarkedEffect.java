package com.zzdzt.endfield_spellbook.effect;

import com.zzdzt.endfield_spellbook.registry.SpellRegistry;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 液氮标记效果
 *
 * 机制：
 * - 被液氮大炮击中的敌人获得此效果，持续 8 秒 (160 ticks)
 * - 效果持续期间，目标每次移动都会受到正比于移动距离的自然伤害
 * - 伤害受施法者法术强度系数加成
 * - 静止目标不受伤
 *
 * 施法者信息存储：
 * - 使用 Forge Entity#getPersistentData() 持久化到实体 NBT
 * - 键: "endfield_spellbook:lnc_caster" (UUID), "endfield_spellbook:lnc_power" (float)
 * - 效果移除时自动清理
 */
public class LiquidNitrogenMarkedEffect extends MagicMobEffect {

    /** 标记持续时间（tick）：8 秒 */
    public static final int EFFECT_DURATION = 20 * 8;

    public static final String NBT_CASTER_UUID = "endfield_spellbook:lnc_caster";
    public static final String NBT_POWER_MULT = "endfield_spellbook:lnc_power";

    /** 低于此移动距离不造成伤害，避免物理微抖动 */
    private static final float MIN_MOVE_THRESHOLD = 0.05f;
    /** 每格移动距离的基础伤害系数 */
    private static final float DAMAGE_PER_BLOCK = 3.0f;
    /** 每级法术等级的伤害倍率增量 (amplifier = spellLevel - 1) */
    private static final float DAMAGE_MULT_PER_LEVEL = 0.20f;

    public LiquidNitrogenMarkedEffect() {
        super(MobEffectCategory.HARMFUL, 0x88CCFF); // 浅蓝色
    }

    /**
     * 在法术施放时调用，将施法者信息存入目标的 persistentData。
     * 在 LiquidNitrogenCannonSpell.onCast 中调用。
     *
     * @param target       被标记的目标
     * @param caster       施法者
     * @param powerMult    施法者的法术强度系数 (getEntityPowerMultiplier)
     */
    public static void storeCasterInfo(LivingEntity target, LivingEntity caster, float powerMult) {
        CompoundTag data = target.getPersistentData();
        data.putUUID(NBT_CASTER_UUID, caster.getUUID());
        data.putFloat(NBT_POWER_MULT, powerMult);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // 每 tick 都触发
    }

    @Override
    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide) return;
        if (!(livingEntity.level() instanceof ServerLevel serverLevel)) return;

        // 计算水平移动速度
        double moveDistance = livingEntity.getDeltaMovement().horizontalDistance();

        // 低温冰雾：环绕目标的白/冰蓝微粒，"越挣扎冻雾越烈"——
        // 粒子量与移动速度正相关，静止时保持稀疏底雾（低亮度）
        if (serverLevel.getGameTime() % 4 == 0) {
            float intensity = (float) Math.min(1.0, moveDistance * 6.0);
            int fogCount = 1 + (int) (intensity * 2);
            io.redspace.ironsspellbooks.capabilities.magic.MagicManager.spawnParticles(
                serverLevel,
                new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.48f, 0.74f, 0.95f), 0.45f + 0.3f * intensity),
                livingEntity.getX(),
                livingEntity.getY() + livingEntity.getBbHeight() * 0.35f,
                livingEntity.getZ(),
                fogCount,
                livingEntity.getBbWidth() * 0.6f,
                livingEntity.getBbHeight() * 0.35f,
                livingEntity.getBbWidth() * 0.6f,
                0.006, false);
        }

        if (moveDistance <= MIN_MOVE_THRESHOLD) return;

        // 读取施法者信息
        CompoundTag data = livingEntity.getPersistentData();
        if (!data.hasUUID(NBT_CASTER_UUID)) return;

        float powerMult = data.getFloat(NBT_POWER_MULT);
        UUID casterUUID = data.getUUID(NBT_CASTER_UUID);

        // 查找施法者（可能已卸载，返回 null）
        Entity casterEntity = serverLevel.getEntity(casterUUID);
        LivingEntity caster = (casterEntity instanceof LivingEntity le) ? le : null;

        //计算伤害
        // damage = moveDistance × powerMultiplier × DAMAGE_PER_BLOCK × (1 + amplifier × 0.15)
        float levelMultiplier = 1.0f + amplifier * DAMAGE_MULT_PER_LEVEL;
        float damage = (float) (moveDistance * powerMult * DAMAGE_PER_BLOCK * levelMultiplier);

        if (damage <= 0) return;

        // 重置无敌帧
        livingEntity.invulnerableTime = 0;

        // 施加伤害
        // 施法者可能为 null（已卸载），用目标自身作为 fallback
        LivingEntity sourceEntity = (caster != null) ? caster : livingEntity;
        var damageSource = SpellRegistry.LIQUID_NITROGEN_CANNON.get()
            .getDamageSource(sourceEntity);
        // 保存当前速度，伤害后恢复——取消伤害带来的击退，保留施法时的初始击退
        Vec3 prevMotion = livingEntity.getDeltaMovement();
        if (livingEntity.hurt(damageSource, damage)) {
            livingEntity.setDeltaMovement(prevMotion);
        }
    }

    @Override
    public void onEffectRemoved(LivingEntity livingEntity, int amplifier) {
        // 清理 persistentData 中的施法者信息
        CompoundTag data = livingEntity.getPersistentData();
        data.remove(NBT_CASTER_UUID);
        data.remove(NBT_POWER_MULT);
        super.onEffectRemoved(livingEntity, amplifier);
    }
}

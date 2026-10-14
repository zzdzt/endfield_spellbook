package com.zzdzt.endfield_spellbook.spell.tidalwave;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WaterTornadoEntity;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WhirlpoolEntity;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.effect.ISyncedMobEffect;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import io.redspace.ironsspellbooks.mixin.LivingEntityAccessor;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 潮汐突进窗口（踏潮卷浪）：仿 ISS {@code VoltStrikeEffect} 的冲刺 MobEffect。
 *
 * <p>窗口内每 tick 检测贴脸敌人/撞墙 → 提前触发潮汐冲击；到期自然移除时
 * （{@link #onEffectRemoved}）若尚未触发则补触发一次（"结束位置产生攻击"，
 * VoltStrike 无此行为）。{@code tidal_wave_struck} 标志保证只触发一次。
 *
 * <p>MobEffect 单例无状态 → 窗口状态全部写宿主 persistentData。
 */
public class TidalWaveDashEffect extends MagicMobEffect implements ISyncedMobEffect {

    /** 宿主 persistentData 键：被消耗涡流的待生成水龙卷数据。 */
    public static final String PENDING_KEY = "tidal_wave_pending";
    /** 宿主 persistentData 键：本冲刺窗已触发冲击的标志（long gameTime）。 */
    public static final String STRUCK_KEY = "tidal_wave_struck";

    public TidalWaveDashEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8FCCFF); // CRYO 蓝白
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity livingEntity, int amplifier) {
        var level = livingEntity.level();
        if (level.isClientSide) {
            return;
        }
        Vec3 delta = livingEntity.getDeltaMovement();

        // ① 实体命中：当前盒沿速度展开为扫掠走廊。冲刺速度 2~3 格/tick 且服务器
        //    位置滞后，VoltStrike 原版薄盒逐 tick 检测会隧穿（目标在两 tick 之间
        //    被整段跳过，表现为穿过敌人不触发）；走廊检测覆盖本 tick 行进路径。
        AABB path = livingEntity.getBoundingBox().expandTowards(delta).inflate(.25, .5, .25);
        boolean hit = false;
        for (Entity entity : level.getEntities(livingEntity, path)) {
            if (entity instanceof LivingEntity && entity.isAlive()) {
                hit = true;
                break;
            }
        }
        // ② 方块命中：Volt 同款下一位置前瞻（玩家移动客户端驱动，服务器看不到
        //    horizontalCollision，必须服务器侧 noCollision 预判）
        if (!hit) {
            hit = !level.noCollision(livingEntity.getBoundingBox()
                .move(delta).move(delta.normalize().scale(0.1)).deflate(0.1));
        }

        // ③ 触发：冲击 + Volt 同款命中反冲（速度反向减速 + 上抛，撞停而非穿行）
        if (hit) {
            triggerImpact(livingEntity, amplifier);
            livingEntity.setDeltaMovement(delta.normalize().scale(-0.5).add(0, 0.5, 0));
            livingEntity.hurtMarked = true;
            livingEntity.removeEffect(this);
            return;
        }
        livingEntity.fallDistance = 0;
    }

    /**
     * 挂效果即置位实体 flag 4（{@code FLAG_SPIN_ATTACK}）——原版激流旋转动画的开关。
     * Spell 侧 {@code setSpinAttackType(RIPTIDE)} 只同步贴图选择，flag 才让动作真正播放。
     */
    @Override
    public void onEffectAdded(LivingEntity pLivingEntity, int pAmplifier) {
        super.onEffectAdded(pLivingEntity, pAmplifier);
        ((LivingEntityAccessor) pLivingEntity).setLivingEntityFlagInvoker(4, true);
    }

    /** 到期补触发：窗口内未触发过且宿主存活则触发；宿主死亡仅清理 pending。 */
    @Override
    public void onEffectRemoved(LivingEntity pLivingEntity, int pAmplifier) {
        super.onEffectRemoved(pLivingEntity, pAmplifier);
        // 先无条件清 flag 4，防止旋转动画残留（双侧都要清，实体数据服务端权威）
        ((LivingEntityAccessor) pLivingEntity).setLivingEntityFlagInvoker(4, false);

        if (pLivingEntity.level().isClientSide) {
            return;
        }
        CompoundTag data = pLivingEntity.getPersistentData();
        if (data.contains(STRUCK_KEY)) {
            return;
        }
        if (pLivingEntity.isAlive()) {
            triggerImpact(pLivingEntity, pAmplifier);
        } else {
            data.remove(PENDING_KEY);
        }
    }

    /**
     * 潮汐冲击：范围寒冷伤害 → 原位生成水龙卷 → 按命中数生成新涡流。
     */
    private static void triggerImpact(LivingEntity host, int amplifier) {
        if (!(host.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = host.getPersistentData();
        data.putLong(STRUCK_KEY, level.getGameTime());

        // ① 寒冷冲击（只伤害，不挂附着——附着规则仅属水龙卷）
        float r = TidalWaveCastCurve.IMPACT_RADIUS;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
            host.getBoundingBox().inflate(r),
            e -> e.isAlive() && !e.is(host) && e.canBeHitByProjectile()
                && e.distanceToSqr(host) < r * r);
        for (LivingEntity victim : victims) {
            DamageSources.applyDamage(victim, amplifier,
                SpellRegistry.TIDAL_WAVE.get().getDamageSource(host));
        }

        // ② 按被消耗涡流记录，在其原位置生成水龙卷
        if (data.contains(PENDING_KEY)) {
            CompoundTag pending = data.getCompound(PENDING_KEY);
            data.remove(PENDING_KEY);
            long age = level.getGameTime() - pending.getLong("t");
            if (pending.getInt("cnt") > 0 && age <= TidalWaveCastCurve.PENDING_STALE_TICKS) {
                float damagePerTick = pending.getFloat("dmg");
                ListTag positions = pending.getList("pos", Tag.TAG_COMPOUND);
                for (int i = 0; i < positions.size(); i++) {
                    CompoundTag p = positions.getCompound(i);
                    Vec3 pos = new Vec3(p.getDouble("x"), p.getDouble("y"), p.getDouble("z"));
                    level.addFreshEntity(new WaterTornadoEntity(
                        EntityRegistry.WATER_TORNADO.get(), level, pos, host, damagePerTick));
                }
            }
        }

        // ③ 按命中敌人数生成新涡流（被命中敌人位置）；按所有者全场钳制：
        //    现存自己的涡流 + 新生成 ≤ WHIRLPOOL_MAX（消耗半径外的残留涡流也计入）
        long existing = level.getEntitiesOfClass(WhirlpoolEntity.class,
            host.getBoundingBox().inflate(TidalWaveCastCurve.WHIRLPOOL_OWNED_SCAN_RANGE),
            w -> !w.isRemoved() && host.getUUID().equals(w.getOwnerId())).size();
        int n = Math.min(victims.size(),
            Math.max(0, TidalWaveCastCurve.WHIRLPOOL_MAX - (int) existing));
        for (int i = 0; i < n; i++) {
            LivingEntity victim = victims.get(i);
            level.addFreshEntity(new WhirlpoolEntity(
                EntityRegistry.TIDAL_WHIRLPOOL.get(), level, victim.position(), host.getUUID()));
        }

        // ④ 落点反馈：Blastwave 环 + 水花 + 音效
        float[] c = EndfieldElement.CRYO.particleColor();
        Vector3f col = new Vector3f(c[0], c[1], c[2]);
        double x = host.getX(), y = host.getY() + 1, z = host.getZ();
        MagicManager.spawnParticles(level, ParticleTypes.SPLASH,
            x, y, z, 40, .4, .4, .4, 0.5, false);
        MagicManager.spawnParticles(level,
            new BlastwaveParticleOptions(col, TidalWaveCastCurve.IMPACT_RADIUS),
            x, host.getY() + .15f, z, 1, 0, 0, 0, 0, true);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, host.getSoundSource(), 2f, 1.1f);
        level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, host.getSoundSource(), 1f, 1.3f);
    }

    /** 冲刺窗客户端：蓝白水流 + 水花残影（对齐 VoltStrikeEffect 密度）。 */
    @Override
    public void clientTick(LivingEntity entity, MobEffectInstance instance) {
        var level = entity.level();
        for (int i = 0; i < 3; i++) {
            Vec3 random = io.redspace.ironsspellbooks.api.util.Utils.getRandomVec3(.25);
            level.addParticle(ParticleTypes.SPLASH,
                entity.getRandomX(0.75), entity.getY() + io.redspace.ironsspellbooks.api.util.Utils.getRandomScaled(0.75),
                entity.getRandomZ(0.75), random.x, random.y, random.z);
        }
        level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.80f, 1.0f), 0.8f),
            entity.getRandomX(0.6), entity.getY() + 0.2, entity.getRandomZ(0.6), 0, 0.04, 0);
    }
}

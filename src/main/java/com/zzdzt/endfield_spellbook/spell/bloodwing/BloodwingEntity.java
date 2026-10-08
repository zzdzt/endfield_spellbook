package com.zzdzt.endfield_spellbook.spell.bloodwing;

import com.zzdzt.endfield_spellbook.entity.CasterOwnedEntity;
import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.ParticleRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 衔火血翼（驱火焚影）：俯冲命中 → 绕主目标盘桓施加虚弱/灼热脆弱 →
 * 目标倒下转移下一个敌人 → Recast 二段蓄势爆裂后收尾消散。
 *
 * <p><b>结算顺序（首击）</b>：AoE 灼热伤害（仅伤害）→ 主目标灼热附着 → 主目标虚弱+灼热脆弱
 * （附着/debuff 仅主目标，且后于伤害结算，防首击吃自身易伤）。
 *
 * <p><b>V1 视觉</b>：隐形载体（NoopRenderer）+ 客户端血萤粒子云（{@code blood_firefly}）；
 * V2 换 MC 蝙蝠换色模型渲染器，机制零改动。
 *
 * <p><b>生命周期</b>：SummonManager 托管（下线恢复/反魔法驱散）；爆裂由自身 tick 倒计时
 * 编排（ISS 无延迟 API，延迟交给实体自身 tick）；onRecastFinished 不回收血翼——
 * 爆裂是异步 4t 收尾，提前回收会吞掉结算。
 */
public class BloodwingEntity extends Entity implements IMagicSummon, AntiMagicSusceptible, CasterOwnedEntity {

    // ==================== 同步 ====================

    private static final EntityDataAccessor<Integer> PHASE =
        SynchedEntityData.defineId(BloodwingEntity.class, EntityDataSerializers.INT);

    /** 客户端可见阶段（IMPACT 为单 tick 结算不单列，随 DIVE 末尾粒子加密表现）。 */
    public enum Phase { ASCEND, DIVE, ORBIT, BURST_WINDUP }

    // ==================== 运行时状态 ====================

    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;
    @Nullable private UUID targetUUID;
    @Nullable private LivingEntity targetCached;
    /** 施法时的法术强度（伤害基数）。 */
    private float spellPower;
    private int transfersLeft = BloodwingCastCurve.MAX_TRANSFERS;

    private Phase phase = Phase.ASCEND;
    private int phaseTick = 0;
    private double orbitAngle = 0.0;
    /** 俯冲贝塞尔 P0（进入 DIVE 时锁定；P2 实时跟随目标）。 */
    @Nullable private Vec3 diveStart;

    /** Recast 二段排队标记（下一次 ORBIT tick 进入蓄势）。 */
    private boolean burstPending = false;
    /** 蓄势锁定的爆裂目标（queueBurst 时的当前目标；其死亡则原位空爆）。 */
    @Nullable private LivingEntity burstTarget;

    public BloodwingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public BloodwingEntity(Level level, LivingEntity owner, LivingEntity target, float spellPower) {
        this(EntityRegistry.BLOODWING.get(), level);
        this.ownerUUID = owner.getUUID();
        this.ownerCached = owner;
        this.targetUUID = target.getUUID();
        this.targetCached = target;
        this.spellPower = spellPower;
        setPos(owner.position().add(0, owner.getBbHeight() * 0.8, 0));
    }

    // ==================== IMagicSummon / 豁免接口 ====================

    @Override
    public void onUnSummon() {
        if (!level().isClientSide) {
            SummonManager.removeSummon(this);
            SummonManager.stopTrackingExpiration(this);
        }
        discard();
    }

    @Override
    public LivingEntity getSummoner() {
        return getOwner();
    }

    @Override
    public void onAntiMagic(MagicData magicData) {
        onUnSummon();
    }

    @Override
    public void onRemovedHelper(Entity entity) {
    }

    @Override
    public boolean shouldIgnoreDamage(DamageSource source) {
        return true;
    }

    @Override
    @Nullable
    public LivingEntity getDirectOwner() {
        return getOwner();
    }

    // ==================== 所有者 / 目标解析 ====================

    @Nullable
    public LivingEntity getOwner() {
        if (ownerCached != null && ownerCached.isAlive()) return ownerCached;
        if (ownerUUID != null && level() instanceof ServerLevel serverLevel
            && serverLevel.getEntity(ownerUUID) instanceof LivingEntity living) {
            ownerCached = living;
            return living;
        }
        return null;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nullable
    public LivingEntity getTarget() {
        if (targetCached != null && targetCached.isAlive() && !targetCached.isRemoved()) return targetCached;
        targetCached = null;
        if (targetUUID != null && level() instanceof ServerLevel serverLevel
            && serverLevel.getEntity(targetUUID) instanceof LivingEntity living && living.isAlive()) {
            targetCached = living;
        }
        return targetCached;
    }

    private void setTarget(@Nullable LivingEntity target) {
        targetCached = target;
        targetUUID = target != null ? target.getUUID() : null;
    }

    // ==================== 同步数据 ====================

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, Phase.ASCEND.ordinal());
    }

    public Phase getPhase() {
        Phase[] values = Phase.values();
        return values[Math.min(entityData.get(PHASE), values.length - 1)];
    }

    private void syncPhase() {
        if (entityData.get(PHASE) != phase.ordinal()) {
            entityData.set(PHASE, phase.ordinal());
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.hasUUID("Target")) targetUUID = tag.getUUID("Target");
        if (tag.contains("SpellPower")) spellPower = tag.getFloat("SpellPower");
        if (tag.contains("Transfers")) transfersLeft = tag.getInt("Transfers");
        if (tag.contains("Phase")) {
            Phase[] values = Phase.values();
            phase = values[Math.max(0, Math.min(tag.getInt("Phase"), values.length - 1))];
        }
        // 重新读档从当前阶段继续：俯冲起点缺失则用当前位置兜底
        if (phase == Phase.DIVE && diveStart == null) diveStart = position();
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        if (targetUUID != null) tag.putUUID("Target", targetUUID);
        tag.putFloat("SpellPower", spellPower);
        tag.putInt("Transfers", transfersLeft);
        tag.putInt("Phase", phase.ordinal());
    }

    // ==================== 主循环 ====================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClient();
        } else {
            tickServer();
        }
    }

    private void tickServer() {
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            onUnSummon();
            return;
        }

        // 爆裂流程不允许被寿命打断（仅 4t，收尾必须完整）
        if (phase != Phase.BURST_WINDUP && tickCount >= BloodwingCastCurve.LIFETIME_TICKS) {
            onUnSummon();
            return;
        }

        phaseTick++;
        switch (phase) {
            case ASCEND -> {
                setPos(getX(), getY() + 0.06, getZ());
                hasImpulse = true;
                if (phaseTick >= BloodwingCastCurve.ASCEND_TICKS) {
                    enterDive();
                }
            }
            case DIVE -> {
                LivingEntity target = getTarget();
                if (target == null && !tryRetarget()) {
                    onUnSummon();
                    return;
                }
                target = getTarget();
                if (target == null) {
                    onUnSummon();
                    return;
                }
                updateDive(target);
                if (phaseTick >= BloodwingCastCurve.DIVE_TICKS) {
                    doImpact(owner, target);
                }
            }
            case ORBIT -> tickOrbit(owner);
            case BURST_WINDUP -> tickBurstWindup(owner);
        }
        syncPhase();
    }

    // ==================== 阶段：俯冲 ====================

    private void enterDive() {
        phase = Phase.DIVE;
        phaseTick = 0;
        diveStart = position();
    }

    /** 二次贝塞尔俯冲：P0 锁定于进入时位置，P1 中点上抬 2.5 格成上抛弧，P2 实时跟随目标胸口。 */
    private void updateDive(LivingEntity target) {
        Vec3 p0 = diveStart != null ? diveStart : position();
        Vec3 p2 = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 p1 = p0.add(p2).scale(0.5).add(0, 2.5, 0);

        float t = BloodwingCastCurve.diveProgress(phaseTick);
        Vec3 prev = position();
        Vec3 a = p0.lerp(p1, t);
        Vec3 b = p1.lerp(p2, t);
        Vec3 pos = a.lerp(b, t);
        setPos(pos.x, pos.y, pos.z);
        hasImpulse = true;

        // 朝向运动方向（V2 蝙蝠渲染用）
        Vec3 dir = pos.subtract(prev);
        if (dir.lengthSqr() > 1e-6) {
            setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            setXRot((float) (-Math.toDegrees(Math.atan2(dir.y, dir.horizontalDistance()))));
        }
    }

    // ==================== 阶段：首击命中（单 tick 结算） ====================

    private void doImpact(LivingEntity owner, LivingEntity mainTarget) {
        var spell = SpellRegistry.BLOODWING.get();
        float damage = spellPower * BloodwingCastCurve.IMPACT_DAMAGE_FRACTION;

        // ① AoE 灼热伤害（仅伤害，不动附着）
        AABB aoe = mainTarget.getBoundingBox().inflate(BloodwingCastCurve.IMPACT_RADIUS);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, aoe, e -> canHit(owner, e))) {
            victim.invulnerableTime = 0;
            DamageSources.applyDamage(victim, damage,
                SpellDamageSource.source(this, owner, spell).setIFrames(0));
        }

        // ②③ 主目标：附着 → debuff（后于伤害结算，防首击吃自身易伤）
        if (canHit(owner, mainTarget)) {
            EndfieldElements.apply(owner, mainTarget, EndfieldElement.HEAT);
            applyDebuffs(mainTarget);
        }

        level().playSound(null, mainTarget.getX(), mainTarget.getY(), mainTarget.getZ(),
            SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 0.9f);

        phase = Phase.ORBIT;
        phaseTick = 0;
        orbitAngle = random.nextDouble() * Math.PI * 2;
    }

    private void applyDebuffs(LivingEntity target) {
        // 玩家不上 debuff（终末地语义为 PvE；附着在 EndfieldElements 内部亦有同款防御）
        if (target instanceof ServerPlayer) return;
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
            BloodwingCastCurve.DEBUFF_DURATION, 0));
        target.addEffect(new MobEffectInstance(EffectRegistry.HEAT_VULNERABLE.get(),
            BloodwingCastCurve.DEBUFF_DURATION, 0));
    }

    /** 命中资格：存活非旁观可选中生物，排除自身/所有者/友军。 */
    private boolean canHit(@Nullable LivingEntity owner, Entity e) {
        if (e == this) return false;
        if (!(e instanceof LivingEntity living) || !living.isAlive()
            || living.isSpectator() || !living.isPickable()) return false;
        if (e instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (owner != null && e == owner) return false;
        return !DamageSources.isFriendlyFireBetween(owner, living);
    }

    // ==================== 阶段：盘桓 ====================

    private void tickOrbit(LivingEntity owner) {
        LivingEntity target = getTarget();

        // 转移判定优先于爆裂排队：目标先死，爆裂跟随新目标
        if (target == null) {
            if (tryRetarget()) {
                enterDive();
            } else {
                onUnSummon(); // 无敌人可转：原地收尾消散
            }
            return;
        }

        // Recast 二段排队 → 进入蓄势收束
        if (burstPending) {
            burstPending = false;
            burstTarget = target;
            phase = Phase.BURST_WINDUP;
            phaseTick = 0;
            return;
        }

        // 轨道运动：水平圆周 + 正弦浮动，朝向切线
        orbitAngle += BloodwingCastCurve.ORBIT_ANGULAR;
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.6, 0);
        double x = center.x + Math.cos(orbitAngle) * BloodwingCastCurve.ORBIT_RADIUS;
        double z = center.z + Math.sin(orbitAngle) * BloodwingCastCurve.ORBIT_RADIUS;
        double y = center.y + Math.sin(tickCount * BloodwingCastCurve.ORBIT_BOB_FREQ) * BloodwingCastCurve.ORBIT_BOB_AMP;
        setPos(x, y, z);
        hasImpulse = true;
        setYRot((float) Math.toDegrees(orbitAngle));

        // debuff 续杯
        if (phaseTick % BloodwingCastCurve.DEBUFF_REFRESH_INTERVAL == 0) {
            applyDebuffs(target);
        }

        // 盘桓灼烧：每秒以主目标为中心小范围 AoE 灼热伤害（附着不重复施加）
        if (phaseTick % BloodwingCastCurve.ORBIT_DAMAGE_INTERVAL == 0) {
            doOrbitDamage(owner, target);
        }

        // 盘桓嗡鸣（占位音效，低频）
        if (phaseTick % 40 == 0) {
            level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.3f, 1.4f);
        }
    }

    /** 盘桓灼烧：每秒 AoE 灼热伤害（主目标为中心 IMPACT_RADIUS；清无敌帧防与其它伤害互相吞）。 */
    private void doOrbitDamage(LivingEntity owner, LivingEntity center) {
        float damage = spellPower * BloodwingCastCurve.ORBIT_DAMAGE_FRACTION;
        AABB aoe = center.getBoundingBox().inflate(BloodwingCastCurve.IMPACT_RADIUS);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, aoe, e -> canHit(owner, e))) {
            victim.invulnerableTime = 0;
            DamageSources.applyDamage(victim, damage,
                SpellDamageSource.source(this, owner, SpellRegistry.BLOODWING.get()).setIFrames(0));
        }
    }

    /** 目标死亡后的转移索敌：原目标位置 8 格内最近有效敌人。 */
    private boolean tryRetarget() {
        if (transfersLeft <= 0) return false;
        LivingEntity owner = getOwner();
        LivingEntity lastTarget = getTarget();
        Vec3 center = lastTarget != null ? lastTarget.position() : position();

        AABB area = new AABB(center, center).inflate(BloodwingCastCurve.RETARGET_RANGE);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, area, e -> canHit(owner, e));
        LivingEntity best = candidates.stream()
            .min(Comparator.comparingDouble(e -> e.position().distanceTo(center)))
            .orElse(null);
        if (best == null) return false;

        transfersLeft--;
        setTarget(best);
        return true;
    }

    // ==================== 阶段：爆裂（Recast 二段收尾） ====================

    /** Recast 二段入口：排队爆裂（已处于爆裂流程时忽略）。 */
    public void queueBurst() {
        if (phase != Phase.BURST_WINDUP) {
            burstPending = true;
        }
    }

    /** 蓄势：血萤向目标胸口收束（位置 lerp 收敛）。 */
    private void tickBurstWindup(LivingEntity owner) {
        Vec3 center = burstTarget != null && burstTarget.isAlive()
            ? burstTarget.position().add(0, burstTarget.getBbHeight() * 0.6, 0)
            : position(); // 目标没了：原地收束空爆
        setPos(position().lerp(center, 0.4));
        hasImpulse = true;
        if (phaseTick >= BloodwingCastCurve.BURST_WINDUP_TICKS) {
            doBurst(owner);
        }
    }

    private void doBurst(LivingEntity owner) {
        // 额外灼热伤害（单体；目标已死则原位空爆纯演出）
        if (burstTarget != null && burstTarget.isAlive()) {
            float damage = spellPower * BloodwingCastCurve.BURST_DAMAGE_FRACTION;
            burstTarget.invulnerableTime = 0;
            DamageSources.applyDamage(burstTarget, damage,
                SpellDamageSource.source(this, owner, SpellRegistry.BLOODWING.get()).setIFrames(0));
        }

        // 爆裂演出：灼热色 Blastwave + Spark 迸发（force 保证最低画质可见）
        if (level() instanceof ServerLevel serverLevel) {
            float[] c = EndfieldElement.HEAT.particleColor();
            Vector3f col = new Vector3f(c[0], c[1], c[2]);
            Vec3 at = position();
            MagicManager.spawnParticles(serverLevel,
                new BlastwaveParticleOptions(col, 1.5f), at.x, at.y, at.z, 1, 0, 0, 0, 0, true);
            MagicManager.spawnParticles(serverLevel,
                new SparkParticleOptions(col), at.x, at.y, at.z, 25, 0.2f, 0.2f, 0.2f, 0.25f, true);
        }
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9f, 0.85f);

        onUnSummon(); // 爆裂 = 收尾消散
    }

    // ==================== 客户端：血萤云（V1 视觉主体） ====================

    private void tickClient() {
        Phase p = getPhase();
        // 蓄势收束期血萤贴身聚集，俯冲期加密，其余常驻
        float jitter = p == Phase.BURST_WINDUP ? 0.12f : 0.6f;
        int count = p == Phase.DIVE ? 4 : 2;
        for (int i = 0; i < count; i++) {
            Vec3 offset = new Vec3(
                (random.nextFloat() - 0.5f) * jitter,
                (random.nextFloat() - 0.5f) * jitter,
                (random.nextFloat() - 0.5f) * jitter);
            Vec3 motion = new Vec3(
                (random.nextFloat() - 0.5f) * 0.04,
                random.nextFloat() * 0.02,
                (random.nextFloat() - 0.5f) * 0.04).add(getDeltaMovement().scale(0.5));
            level().addParticle(ParticleRegistry.BLOOD_FIREFLY.get(),
                getX() + offset.x, getY() + offset.y, getZ() + offset.z,
                motion.x, motion.y, motion.z);
        }
        // 俯冲/蓄势：火焰细节点缀（"衔火"）
        if ((p == Phase.DIVE || p == Phase.BURST_WINDUP) && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(),
                (random.nextFloat() - 0.5f) * 0.02, (random.nextFloat() - 0.5f) * 0.02,
                (random.nextFloat() - 0.5f) * 0.02);
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        // 消散演出：血萤迸散
        if (level().isClientSide) {
            for (int i = 0; i < 10; i++) {
                level().addParticle(ParticleRegistry.BLOOD_FIREFLY.get(),
                    getX() + (random.nextDouble() - 0.5) * 0.6,
                    getY() + (random.nextDouble() - 0.5) * 0.6,
                    getZ() + (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.15, random.nextDouble() * 0.1,
                    (random.nextDouble() - 0.5) * 0.15);
            }
        }
        super.remove(reason);
    }

    // ==================== 杂项 ====================

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            onAntiMagic(null);
            return true;
        }
        return false;
    }
}

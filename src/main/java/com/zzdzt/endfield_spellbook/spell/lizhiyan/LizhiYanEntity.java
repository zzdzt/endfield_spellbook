package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import com.zzdzt.endfield_spellbook.entity.CasterOwnedEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LizhiYanEntity extends Entity implements IMagicSummon, AntiMagicSusceptible, CasterOwnedEntity {

    //同步数据
    private static final EntityDataAccessor<Integer> ATTACK_PHASE =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CHARGE_SCALE =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_THRUSTING =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AIM_YAW =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);

    enum Phase { IDLE, CHARGE, STAB, BLAST, BLAST_DELAY, SPIN, COOLDOWN, BLAST_READY }

    //排列配置
    private static final Vec3[] FORMATION_OFFSETS = {
        new Vec3(-2.0, 1.2, 0.0),
        new Vec3(2.0, 1.2, 0.0),
        new Vec3(0.0, 1.8, -1.5)
    };

    private static final Vec3[] DEFAULT_AIM = {
        new Vec3(-0.5, 0, 0.8).normalize(),
        new Vec3(0.5, 0, 0.8).normalize(),
        new Vec3(0.0, 0.1, 1.0).normalize()
    };

    private static final float[][] BLAST_TINTS = {
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f}
    };

    //性能优化常量
    private static final float FORMATION_LERP = 0.12f;
    private static final float IDLE_SWAY_AMP = 0.03f;
    private static final float IDLE_SWAY_FREQ = 0.02f;
    // 视线检测降频：每 2 tick 一次
    private static final int LOS_CHECK_INTERVAL = 2;
    // 丢失视线超时：13 * 2tick = 26tick ≈ 1.3秒
    private static final int LOST_SIGHT_TIMEOUT = 13;
    // 目标扫描间隔：无目标时更频繁
    private static final int SCAN_INTERVAL_NO_TARGET = 10;
    private static final int SCAN_INTERVAL_HAS_TARGET = 20;
    // 方向变化阈值：小于此值时跳过三角函数
    private static final double AIM_DIR_THRESHOLD_SQ = 0.001;
    // 位置更新降频：每 2 tick 计算一次
    private static final int POSITION_UPDATE_INTERVAL = 2;

    //攻击时序
    private static final int CHARGE_TICKS_STAB = 10;
    private static final int COOLDOWN_TICKS_STAB = 10;
    private static final int COOLDOWN_TICKS_BLAST = 40;
    private static final float[] STAB_DAMAGE_MULTIPLIERS = {1.0f, 1.3f};
    private static final float BLAST_DAMAGE_MULTIPLIER = 3.0f;
    private static final int RETURN_TICKS = 10;
    // 冲刺硬超时兜底（tick）：目标乱跑追不上时放弃穿刺，防止相位机卡死
    private static final int STAB_MAX_TICKS = 60;
    // 协同光束等待超时（tick）：三剑都完成前两段穿刺后，等它们全部就绪再齐射；超时强制放行
    private static final int BLAST_READY_TIMEOUT = 60;
    // 三剑全部就位后的聚气停留（tick）：给三剑对齐时间 + 蓄力观感
    private static final int BLAST_CHARGE_TICKS = 6;

    //旋转动作时序
    private static final int DEFAULT_SPIN_TICKS = 50;
    private static final float SPIN_RADIUS = 1.5f;
    private static final float SPIN_HEIGHT = 1.2f;
    private static final float SPIN_CENTER_DISTANCE = 2.0f;
    private static final int BLAST_TO_SPIN_DELAY = 4;
    private int spinTicks = DEFAULT_SPIN_TICKS;

    //运行时状态
    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;
    private int slotIndex;
    private float baseDamage;
    private float range = 20.0f;

    // 目标追踪
    @Nullable private Entity currentTarget;
    @Nullable private Entity pendingTarget;
    private int lostSightTick;

    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int attackIndex = 0;

    // 位置缓存
    private Vec3 formationPos = Vec3.ZERO;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private float lastOwnerYaw = 0f;
    private double lastCosYaw = 1.0, lastSinYaw = 0.0;
    private int lastFormationComputeTick = -1;

    private Vec3 thrustTarget = Vec3.ZERO;
    private int thrustTick = 0;
    @Nullable private Vec3 thrustDirection;
    private boolean hasDealtDamage = false;
    // 穿刺相位机（参照 YujianCraft 阻尼追踪）：后顿 → 冲刺 → 惯性滑行，物理时长
    private enum ThrustStage { WINDUP, DASH, THROUGH }
    @Nullable private ThrustStage thrustStage;
    @Nullable private Vec3 thrustWaypoint;
    private int thrustTimeout;

    private int returnTick = 0;

    @Nullable private Vec3 blastLockedDirection;
    // 协同光束站位锁定：进 BLAST_READY 时锁定一次，之后不随玩家移动漂移，
    // 否则 closeTo 判定永远追不上移动目标 → 各自超时错峰发射
    @Nullable private Vec3 blastReadyPos;
    // 三剑全部就位后的聚气停留计数（barrier 通过后递增，到 BLAST_CHARGE_TICKS 才发射）
    private int blastReadyHold;

    // 方向缓存
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private Vec3 lastAimDir = Vec3.ZERO;

    public LizhiYanEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LizhiYanEntity(EntityType<?> entityType, Level level,
                          LivingEntity owner, int slot, int maxSlot) {
        this(entityType, level);
        this.ownerUUID = owner.getUUID();
        this.ownerCached = owner;
        this.slotIndex = slot;
        this.baseDamage = 6.0f;

        Vec3 aim = DEFAULT_AIM[slot];
        this.currentYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        this.currentPitch = (float) -Math.toDegrees(
            Math.atan2(aim.y, Math.sqrt(aim.x*aim.x + aim.z*aim.z)));

        setInitialPosition(owner);
    }

    @Override public void onUnSummon() {
        if (!level().isClientSide) {
            SummonManager.removeSummon(this);
            SummonManager.stopTrackingExpiration(this);
        }
        discard();
    }
    @Override public LivingEntity getSummoner() { return getOwner(); }
    @Override public void onAntiMagic(MagicData magicData) { onUnSummon(); }
    @Override public void onRemovedHelper(Entity entity) {}
    @Override public boolean shouldIgnoreDamage(DamageSource source) { return true; }

    public void setBaseDamage(float damage) { this.baseDamage = damage; }
    public void setRange(float range) { this.range = range; }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerCached != null && ownerCached.isAlive()) return ownerCached;
        if (ownerUUID != null && level() instanceof ServerLevel sl) {
            var e = sl.getEntity(ownerUUID);
            if (e instanceof LivingEntity living) {
                ownerCached = living;
                return living;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public LivingEntity getDirectOwner() {
        return getOwner();
    }

    //数据同步
    @Override protected void defineSynchedData() {
        entityData.define(ATTACK_PHASE, 0);
        entityData.define(CHARGE_SCALE, 1.0f);
        entityData.define(IS_THRUSTING, false);
        entityData.define(AIM_YAW, 0.0f);
        entityData.define(AIM_PITCH, 0.0f);
    }
    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Slot")) slotIndex = tag.getInt("Slot");
        if (tag.contains("BaseDamage")) baseDamage = tag.getFloat("BaseDamage");
        if (tag.contains("Range")) range = tag.getFloat("Range");
        if (tag.contains("AttackIndex")) attackIndex = tag.getInt("AttackIndex");
    }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Slot", slotIndex);
        tag.putFloat("BaseDamage", baseDamage);
        tag.putFloat("Range", range);
        tag.putInt("AttackIndex", attackIndex);
    }

    //公共 Getter
    public int getAttackPhase() { return entityData.get(ATTACK_PHASE); }
    public float getChargeScale() { return entityData.get(CHARGE_SCALE); }
    public boolean isThrusting() { return entityData.get(IS_THRUSTING); }
    public float getAimYaw() { return entityData.get(AIM_YAW); }
    public float getAimPitch() { return entityData.get(AIM_PITCH); }
    public int getSlotIndex() { return slotIndex; }
    public void setSpinTicks(int ticks) { this.spinTicks = ticks; }

    //主循环
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) tickClient();
        else tickServer();
    }

    private void tickClient() {
        if (firstTick) spawnSummonParticles();

        Phase currentPhase = Phase.values()[entityData.get(ATTACK_PHASE)];
        float chargeScale = entityData.get(CHARGE_SCALE);
        boolean isThrusting = entityData.get(IS_THRUSTING);

        // 蓄力粒子环
        if (currentPhase == Phase.CHARGE && tickCount % 5 == 0) {
            float angle = tickCount * 0.3f + slotIndex * 2.1f;
            for (int i = 0; i < 2; i++) {
                float a = angle + (float) (Math.PI * i);
                double px = getX() + Math.cos(a) * chargeScale * 0.4;
                double pz = getZ() + Math.sin(a) * chargeScale * 0.4;
                level().addParticle(ParticleTypes.ENCHANT, px, getY(), pz, 0, 0.03, 0);
            }
        }

        // 旋转粒子
        if (currentPhase == Phase.SPIN && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.ENCHANT,
                getX() + (random.nextDouble()-0.5)*0.3,
                getY() + (random.nextDouble()-0.5)*0.3,
                getZ() + (random.nextDouble()-0.5)*0.3,
                (random.nextDouble()-0.5)*0.1,
                (random.nextDouble()-0.5)*0.1,
                (random.nextDouble()-0.5)*0.1);
        }

        // 冲刺拖尾
        if (isThrusting && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.CRIT,
                getX() + (random.nextDouble()-0.5)*0.2,
                getY() + (random.nextDouble()-0.5)*0.2,
                getZ() + (random.nextDouble()-0.5)*0.2,
                0, 0, 0);
        }

        // 常驻粒子
        if (tickCount % 10 == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), 0, 0.03, 0);
        }
    }

    private void tickServer() {
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { onUnSummon(); return; }

        if (returnTick > 0) { updateReturnToFormation(owner); syncStateToClient(); return; }
        if (thrustTick > 0) { updateThrust(); }
        else if (phase == Phase.SPIN) { updateSpin(owner); }
        else if (phase == Phase.BLAST_READY) { updateBlastReadyPosition(owner); }
        else if (phase == Phase.BLAST || phase == Phase.BLAST_DELAY) { holdBlastPosition(owner); }
        else { updateFormationPosition(owner); }

        updateTarget(owner);
        updateAttackState(owner);
        syncStateToClient();
    }

    // 位置
    private void updateFormationPosition(LivingEntity owner) {
        // 缓存 owner 的 yaw 三角函数，避免每 tick 重复计算
        float ownerYaw = owner.getYRot();
        if (ownerYaw != lastOwnerYaw || lastOwnerPos.distanceToSqr(owner.position()) > 0.01) {
            lastOwnerYaw = ownerYaw;
            lastOwnerPos = owner.position();
            double yawRad = ownerYaw * 0.0174533f;
            lastCosYaw = Math.cos(yawRad);
            lastSinYaw = Math.sin(yawRad);
        }

        // 每 2 tick 计算一次目标位置，中间用 lerp 插值
        Vec3 targetPos;
        if (tickCount % POSITION_UPDATE_INTERVAL == 0 || lastFormationComputeTick < 0) {
            // 完整计算
            Vec3 offset = FORMATION_OFFSETS[slotIndex];
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            targetPos = owner.position().add(rx, offset.y, rz);

            double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
            targetPos = targetPos.add(0, swayY, 0);

            lastFormationComputeTick = tickCount;
        } else {
            // 使用上次计算的位置
            Vec3 offset = FORMATION_OFFSETS[slotIndex];
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            targetPos = owner.position().add(rx, offset.y, rz);
            double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
            targetPos = targetPos.add(0, swayY, 0);
        }

        Vec3 newPos = position().lerp(targetPos, FORMATION_LERP);
        setPos(newPos.x, newPos.y, newPos.z);
        hasImpulse = true;
        formationPos = newPos;
        updateAimDirection(owner);
    }

    //瞄准方向
    private void updateAimDirection(LivingEntity owner) {
        Entity targetToAim = currentTarget != null ? currentTarget : pendingTarget;

        Vec3 aimDir;
        if (targetToAim != null && targetToAim.isAlive()) {
            Vec3 tc = targetToAim.position().add(0, targetToAim.getBbHeight()/2, 0);
            aimDir = tc.subtract(position()).normalize();
        } else {
            Vec3 def = DEFAULT_AIM[slotIndex];
            double rx = def.x * lastCosYaw - def.z * lastSinYaw;
            double rz = def.x * lastSinYaw + def.z * lastCosYaw;
            aimDir = new Vec3(rx, def.y, rz).normalize();
        }

        // 方向变化很小时跳过三角函数计算
        if (aimDir.distanceToSqr(lastAimDir) < AIM_DIR_THRESHOLD_SQ) {
            // 使用缓存的 yaw/pitch，只更新 entity 的 rotation
            setYRot(currentYaw);
            setXRot(currentPitch);
            yRotO = currentYaw;
            xRotO = currentPitch;
            return;
        }
        lastAimDir = aimDir;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-aimDir.x, aimDir.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(aimDir.y, Math.sqrt(aimDir.x*aimDir.x + aimDir.z*aimDir.z)));
        currentYaw = lerpAngle(currentYaw, targetYaw, 0.15f);
        currentPitch = lerpAngle(currentPitch, targetPitch, 0.15f);
        setYRot(currentYaw);
        setXRot(currentPitch);
        yRotO = currentYaw;
        xRotO = currentPitch;
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return from + diff * t;
    }

    // 目标追踪
    private void updateTarget(LivingEntity owner) {
        // 1. 获取玩家当前攻击目标
        Entity newPlayerTarget = getPlayerCurrentTarget(owner);

        // 2. 如果玩家有目标，优先使用
        if (newPlayerTarget != null && newPlayerTarget.isAlive()) {
            UUID currentUUID = currentTarget != null ? currentTarget.getUUID() : null;
            UUID bestUUID = newPlayerTarget.getUUID();

            if (!bestUUID.equals(currentUUID)) {
                boolean inRange = distanceTo(newPlayerTarget) <= range;
                boolean hasLos = hasLineOfSight(newPlayerTarget);

                if (inRange && hasLos) {
                    pendingTarget = newPlayerTarget;
                }
            }
        }

        // 3. 检查当前目标是否有效
        if (pendingTarget == null && currentTarget != null) {
            if (!currentTarget.isAlive() || currentTarget.isRemoved()) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (distanceTo(currentTarget) > range * 1.5) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (tickCount % LOS_CHECK_INTERVAL == 0 && !hasLineOfSight(currentTarget)) {
                lostSightTick++;
                if (lostSightTick >= LOST_SIGHT_TIMEOUT) {
                    currentTarget = null;
                    lostSightTick = 0;
                }
            } else if (tickCount % LOS_CHECK_INTERVAL == 0) {
                // 有视线时重置计时
                lostSightTick = 0;
            }
        }

        // 4. 自动扫描
        if (currentTarget == null && pendingTarget == null) {
            int scanInterval = (currentTarget == null && pendingTarget == null)
                ? SCAN_INTERVAL_NO_TARGET
                : SCAN_INTERVAL_HAS_TARGET;
            if (tickCount % scanInterval == 0) {
                currentTarget = findNearestEnemy(owner);
            }
        }
    }

    /**
     * 获取玩家当前攻击目标
     */
    @Nullable
    private Entity getPlayerCurrentTarget(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer serverPlayer)) return null;

        // 原版 lastHurtMob（100tick内）
        LivingEntity lastHurt = serverPlayer.getLastHurtMob();
        if (lastHurt != null && lastHurt.isAlive()) {
            int lastHurtTimestamp = serverPlayer.getLastHurtMobTimestamp();
            if (serverPlayer.tickCount - lastHurtTimestamp < 100) {
                return lastHurt;
            }
        }

        return null;
    }

    @Nullable
    private Entity findNearestEnemy(LivingEntity owner) {
        AABB aabb = new AABB(getX()-range, getY()-range, getZ()-range,
                             getX()+range, getY()+range, getZ()+range);
        var candidates = level().getEntities(owner, aabb, e -> {
            if (e == this || e == owner) return false;
            if (!(e instanceof LivingEntity living)) return false;
            if (e instanceof net.minecraft.world.entity.player.Player) return false;
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        return candidates.stream()
            .filter(e -> hasLineOfSight(e))
            .min((a, b) -> Double.compare(distanceTo(a), distanceTo(b)))
            .orElse(null);
    }

    private boolean hasLineOfSight(Entity target) {
        Vec3 start = position().add(0, 0.3, 0);
        Vec3 end = target.position().add(0, target.getBbHeight()/2, 0);
        return level().clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // 攻击状态机
    private void updateAttackState(LivingEntity owner) {
        switch (phase) {
            case IDLE:
                if (pendingTarget != null && pendingTarget.isAlive()) {
                    currentTarget = pendingTarget;
                    pendingTarget = null;
                    attackIndex = 0;
                    lostSightTick = 0;
                }

                if (currentTarget != null && currentTarget.isAlive()) {
                    phase = Phase.CHARGE;
                    phaseTick = 0;
                    blastLockedDirection = null;
                    hasDealtDamage = false;
                }
                break;

            case CHARGE:
                phaseTick++;
                int chargeThreshold = CHARGE_TICKS_STAB;

                if (currentTarget != null) updateAimDirection(owner);

                if (attackIndex >= 2 && currentTarget != null) {
                    Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
                    blastLockedDirection = tc.subtract(position()).normalize();
                }

                float chargeProgress = phaseTick / (float) chargeThreshold;
                float pulse = 1.0f + (float) Math.sin(chargeProgress * Math.PI) * 0.25f;
                entityData.set(CHARGE_SCALE, pulse);

                if (phaseTick >= chargeThreshold) {
                    entityData.set(CHARGE_SCALE, 1.0f);
                    if (attackIndex < 2) startStab();
                    else startBlastReady();
                }
                break;

            case STAB:
                // 穿刺由 tickServer 主循环的 updateThrust 相位机驱动（thrustTick > 0），
                // 这里只做硬超时兜底：DASH 追不上目标等异常时强制收尾
                phaseTick++;
                if (phaseTick >= STAB_MAX_TICKS) finishThrust();
                break;

            case BLAST_READY:
                // 协同光束屏障：三剑各自完成 stab×2 后在此飞向齐射站位，全部到位后**先聚气停留**
                // 再齐射（让三剑有时间对齐 + 蓄力观感），之后一起进入 BLAST_DELAY → 同步 SPIN
                phaseTick++;
                if (allAtBlastPositions(owner)) blastReadyHold++;
                else blastReadyHold = 0;
                if (blastReadyHold >= BLAST_CHARGE_TICKS || phaseTick >= BLAST_READY_TIMEOUT) {
                    startBlast(owner);
                    blastReadyHold = 0;
                }
                break;

            case BLAST:
                phase = Phase.BLAST_DELAY;
                phaseTick = 0;
                break;

            case BLAST_DELAY:
                phaseTick++;
                if (phaseTick >= BLAST_TO_SPIN_DELAY) {
                    phase = Phase.SPIN;
                    phaseTick = 0;
                }
                break;

            case SPIN:
                phaseTick++;
                if (phaseTick >= spinTicks) {
                    attackIndex = 0;
                    phase = Phase.COOLDOWN;
                    phaseTick = 0;
                    // 释放本轮齐射站位锁定与聚气计数，下一轮重新锁定
                    blastReadyPos = null;
                    blastReadyHold = 0;
                }
                break;

            case COOLDOWN:
                phaseTick++;
                int cooldown = (attackIndex == 0) ? COOLDOWN_TICKS_BLAST : COOLDOWN_TICKS_STAB;

                if (phaseTick >= cooldown) {
                    if (pendingTarget != null && pendingTarget.isAlive()) {
                        currentTarget = pendingTarget;
                        pendingTarget = null;
                        attackIndex = 0;
                        phase = Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        hasDealtDamage = false;
                    } else if (currentTarget != null && currentTarget.isAlive()) {
                        phase = Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        hasDealtDamage = false;
                    } else {
                        phase = Phase.IDLE;
                        phaseTick = 0;
                        attackIndex = 0;
                    }
                }
                break;


        }
    }

    // 戳刺（阻尼追踪相位机：后顿 → 冲刺 → 惯性滑行，物理时长，参照 YujianCraft flyToward）
    private void startStab() {
        if (currentTarget == null) { phase = Phase.IDLE; phaseTick = 0; return; }
        phase = Phase.STAB; phaseTick = 0; thrustTick = 1; hasDealtDamage = false;
        formationPos = position();
        Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
        Vec3 toTarget = targetCenter.subtract(formationPos);
        thrustDirection = toTarget.normalize();
        thrustTarget = targetCenter;
        thrustStage = ThrustStage.WINDUP;
        thrustWaypoint = formationPos.subtract(thrustDirection.scale(0.6)); // 蓄力后拉：向起点后方顿
        thrustTimeout = 0;
        entityData.set(IS_THRUSTING, true);
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3f, 1.5f);
    }

    private void updateThrust() {
        if (thrustStage == null) { finishThrust(); return; }
        switch (thrustStage) {
            case WINDUP -> {
                // 蓄力后拉：位置向起点后方顿，但剑尖立即朝目标（turnRate=1.0，
                // 否则 0.28 + 短持续时间跟不上，剑仍朝上次返回的朝向→视觉上枪尾朝前）
                flyToward(thrustWaypoint, 0.30, 0.50, thrustDirection, 1.0);
                if (closeTo(thrustWaypoint, 0.15)) {
                    thrustStage = ThrustStage.DASH;
                    thrustTimeout = 0;
                }
            }
            case DASH -> {
                // 冲刺：目标点实时追踪，全速穿越；剑尖始终朝目标；命中用扫掠判定
                LivingEntity target = currentTarget instanceof LivingEntity living ? living : null;
                Vec3 aimPoint = (target != null && target.isAlive())
                        ? target.position().add(0, target.getBbHeight() * 0.55, 0)
                        : thrustTarget;
                Vec3 previous = position();
                flyToward(aimPoint, 0.25, 1.18, aimPoint.subtract(position()).normalize());
                if (!hasDealtDamage && target != null) {
                    var box = target.getBoundingBox().inflate(0.3);
                    if (box.contains(position()) || box.clip(previous, position()).isPresent()) {
                        applyStabDamage(target);
                        hasDealtDamage = true;
                    }
                }
                boolean reached = aimPoint.distanceToSqr(position()) < 0.4 * 0.4;
                if (hasDealtDamage || reached) {
                    // 惯性滑行：命中后沿当前动量方向滑出去（贯穿是物理延续，非设计过冲点）
                    Vec3 travel = getDeltaMovement();
                    if (travel.lengthSqr() < 1e-6) travel = thrustDirection != null ? thrustDirection : new Vec3(0, 0, 1);
                    thrustWaypoint = position().add(travel.normalize().scale(1.5)).add(0, 0.2, 0);
                    thrustStage = ThrustStage.THROUGH;
                } else if (++thrustTimeout > 30) {
                    finishThrust(); // 追不上，放弃穿刺
                }
            }
            case THROUGH -> {
                if (thrustWaypoint == null) { finishThrust(); return; }
                flyToward(thrustWaypoint, 0.14, 0.96);
                if (closeTo(thrustWaypoint, 0.2)) finishThrust();
            }
        }
    }

    private void updateReturnToFormation(LivingEntity owner) {
        // 编队位（含 idle 摆动），与 updateFormationPosition 同款缓存三角函数
        Vec3 offset = FORMATION_OFFSETS[slotIndex];
        double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
        double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
        Vec3 formationTarget = owner.position().add(rx, offset.y, rz);
        double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
        formationTarget = formationTarget.add(0, swayY, 0);

        // 阻尼追踪回巢（参照 YujianCraft tickReturnApproach）：柔顺入位，剑尖朝编队位
        flyToward(formationTarget, 0.22, 0.82, formationTarget.subtract(position()).normalize());
        if (closeTo(formationTarget, 0.25) || --returnTick <= 0) {
            returnTick = 0;
        }
    }

    /** 收尾穿刺：第一次穿刺后回编队冷却；第二次穿刺后直接进入协同光束准备（飞向齐射站位）。 */
    private void finishThrust() {
        thrustTick = 0;
        thrustStage = null;
        thrustWaypoint = null;
        hasDealtDamage = false;
        entityData.set(IS_THRUSTING, false);
        attackIndex++;
        if (attackIndex >= 2) {
            // 第二次穿刺完成：直接飞向齐射站位（身后偏左），跳过 COOLDOWN/CHARGE 往返，
            // 避免"先回编队位 → 再瞬移到身后偏左"的断裂感
            phase = Phase.BLAST_READY;
            phaseTick = 0;
            blastReadyHold = 0;
            returnTick = 0;
        } else {
            returnTick = RETURN_TICKS;
            phase = Phase.COOLDOWN;
            phaseTick = 0;
        }
    }

    // ===== 阻尼追踪运动（参照 YujianCraft FlyingSwordEntity）=====

    /**
     * 阻尼追踪：期望速度 = min(maxSpeed, 剩余距离)（arrive 减速），
     * 当前动量向期望速度 lerp（steering 越小转向越柔），接近目标自动减速刹车。
     * 相比"关键帧 setPos"，速度曲线物理连续 —— 穿刺灵动的核心。
     */
    private void flyToward(Vec3 destination, double steering, double maxSpeed) {
        flyToward(destination, steering, maxSpeed, null, 0.28);
    }

    /**
     * @param facing 剑尖朝向（null = 默认朝运动方向 motion）。
     *               蓄力后拉等"位置后退但剑尖仍要朝目标"的场景必须显式传 facing，
     *               否则剑尖会朝后退方向（表现为枪尾朝前）。
     */
    private void flyToward(Vec3 destination, double steering, double maxSpeed, @Nullable Vec3 facing) {
        flyToward(destination, steering, maxSpeed, facing, 0.28);
    }

    /**
     * @param turnRate 渐进转向速率（0~1）。WINDUP 等"必须立即对准"的场景传 1.0，
     *                 避免 turnRate 0.28 + 短持续时间导致转向跟不上、剑仍朝上次朝向。
     */
    private void flyToward(Vec3 destination, double steering, double maxSpeed,
                           @Nullable Vec3 facing, double turnRate) {
        Vec3 offset = destination.subtract(position());
        Vec3 desired = offset.lengthSqr() < 1e-6 ? Vec3.ZERO
                : offset.normalize().scale(Math.min(maxSpeed, Math.max(0.08, offset.length())));
        Vec3 motion = getDeltaMovement().scale(1.0 - steering).add(desired.scale(steering));
        if (motion.length() > maxSpeed) motion = motion.normalize().scale(maxSpeed);
        setDeltaMovement(motion);
        setPos(position().add(motion));
        hasImpulse = true;
        Vec3 face = facing != null ? facing : motion;
        if (face.lengthSqr() > 1e-5) faceDirection(face, turnRate);
    }

    /** 朝向向量渐进转向（turnRate 0~1，越小越慢），避免转向突变。 */
    private void faceDirection(Vec3 requested, double turnRate) {
        Vec3 current = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 target = requested.lengthSqr() < 1e-6 ? current : requested.normalize();
        Vec3 blended = current.scale(1.0 - turnRate).add(target.scale(turnRate));
        if (blended.lengthSqr() < 1e-6) blended = target;
        blended = blended.normalize();
        currentYaw = (float) Math.toDegrees(Math.atan2(-blended.x, blended.z));
        currentPitch = (float) -Math.toDegrees(Math.atan2(blended.y, blended.horizontalDistance()));
        setYRot(currentYaw);
        setXRot(currentPitch);
    }

    private boolean closeTo(Vec3 point, double radius) {
        return point.distanceToSqr(position()) < radius * radius;
    }

    private void updateSpin(LivingEntity owner) {
        double progress = phaseTick / (double) spinTicks;

        double baseAngle = slotIndex * (2 * Math.PI / 3);
        double spinAngle = baseAngle - progress * 2 * Math.PI;

        float yawRad = owner.getYRot() * 0.0174533f;

        double behindX = Math.sin(yawRad) * SPIN_CENTER_DISTANCE;
        double behindZ = -Math.cos(yawRad) * SPIN_CENTER_DISTANCE;

        double centerX = owner.getX() + behindX;
        double centerY = owner.getY() + SPIN_HEIGHT;
        double centerZ = owner.getZ() + behindZ;

        double offsetRight = Math.cos(spinAngle) * SPIN_RADIUS;
        double offsetUp = Math.sin(spinAngle) * SPIN_RADIUS;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double x = centerX + offsetRight * rightX;
        double y = centerY + offsetUp;
        double z = centerZ + offsetRight * rightZ;

        Vec3 targetPos = new Vec3(x, y, z);

        Vec3 newPos = position().lerp(targetPos, 0.2f);
        setPos(newPos.x, newPos.y, newPos.z);
        hasImpulse = true;

        Vec3 center = new Vec3(centerX, centerY, centerZ);
        Vec3 fromCenter = newPos.subtract(center).normalize();

        currentYaw = (float) Math.toDegrees(Math.atan2(-fromCenter.x, fromCenter.z));
        currentPitch = (float) -Math.toDegrees(Math.asin(fromCenter.y));
        setYRot(currentYaw);
        setXRot(currentPitch);
    }

    private void applyStabDamage(LivingEntity target) {
        LivingEntity owner = getOwner();
        if (owner == null) return;
        float multiplier = STAB_DAMAGE_MULTIPLIERS[
            Math.min(attackIndex, STAB_DAMAGE_MULTIPLIERS.length - 1)];
        float damage = baseDamage * multiplier;
        // 官方模式（EchoingArrowProjectile）：直接从 registry 取法术引用构造伤害来源
        var spell = SpellRegistry.LIZHI_YAN.get();
        SpellDamageSource source = SpellDamageSource.source(this, owner, spell).setIFrames(5);
        DamageSources.applyDamage(target, damage, source);
    }

    // 协同光束：进入归位等待态（三剑都就绪后一起 startBlast）
    private void startBlastReady() {
        phase = Phase.BLAST_READY;
        phaseTick = 0;
        blastReadyHold = 0;
        entityData.set(CHARGE_SCALE, 1.0f);
    }

    /** 同 owner 的其他飞剑（存活）。用于协同光束 / SPIN 屏障。 */
    private List<LizhiYanEntity> getSiblingSwords() {
        if (ownerUUID == null || !(level() instanceof ServerLevel)) return List.of();
        return level().getEntitiesOfClass(LizhiYanEntity.class,
                getBoundingBox().inflate(96), e -> e != this && ownerUUID.equals(e.ownerUUID));
    }

    /**
     * 协同光束站位：玩家<b>身后偏左</b>、竖直三角分布——光束从身后射出、不穿玩家身体。
     * 抽出公共方法：barrier 也要用它判断"是否到达站位"，避免剑没飞到位就齐射。
     */
    private Vec3 computeBlastPosition(LivingEntity owner, int slot) {
        float yawRad = owner.getYRot() * 0.0174533f;
        double sin = Math.sin(yawRad), cos = Math.cos(yawRad);
        Vec3 center = owner.position()
                .add(sin * 2.5, 1.4, cos * 2.5)          // 身后
                .add(-cos * 1.6, 0, -sin * 1.6);         // 偏左
        double angle = slot * 2.0 * Math.PI / 3.0 + Math.PI / 2.0;
        Vec3 right = new Vec3(cos, 0, sin);
        return center.add(right.scale(Math.cos(angle) * 0.7))
                .add(0, Math.sin(angle) * 0.7, 0);
    }

    private void updateBlastReadyPosition(LivingEntity owner) {
        // 惰性锁定：进入 BLAST_READY 后第一次调用时固定站位，之后不再漂移
        if (blastReadyPos == null) blastReadyPos = computeBlastPosition(owner, slotIndex);
        flyToward(blastReadyPos, 0.30, 1.0, aimFacing(owner));
    }

    /** 齐射后悬停在锁定站位（蓄力/后摇），保持三剑阵型与瞄准，直到 SPIN 把剑拉回身后环。 */
    private void holdBlastPosition(LivingEntity owner) {
        if (blastReadyPos == null) return;
        flyToward(blastReadyPos, 0.25, 0.6, aimFacing(owner));
    }

    /** 剑尖朝向：目标存活时朝目标（0.55 身高），否则朝玩家前方。 */
    private Vec3 aimFacing(LivingEntity owner) {
        if (currentTarget != null && currentTarget.isAlive()) {
            return currentTarget.position().add(0, currentTarget.getBbHeight() / 2, 0)
                    .subtract(position()).normalize();
        }
        float yawRad = owner.getYRot() * 0.0174533f;
        return new Vec3((float) -Math.sin(yawRad), 0, (float) -Math.cos(yawRad));
    }

    /**
     * 协同光束屏障（严格版）：所有剑 phase 在 blast 序列 + 各自 closeTo 自己的锁定站位，
     * 才一起齐射。仅靠 phase 序列不够——phase 进入早但飞行未到位就齐射会出现"还在编队位就开枪"。
     */
    private boolean allAtBlastPositions(LivingEntity owner) {
        for (LizhiYanEntity s : getSiblingSwords()) {
            if (!s.swordReadyAndAtPos(owner)) return false;
        }
        return swordReadyAndAtPos(owner);
    }

    private boolean swordReadyAndAtPos(LivingEntity owner) {
        Phase p = phase;
        // 已发射 / 后摇中的剑（BLAST、BLAST_DELAY、SPIN）：不再做就位判定。
        // ⚠️ 关键：startBlast 后剑会离开齐射站位，若仍要求 closeTo，其余剑会判定它"未就位"
        // 而一直卡到超时 —— 这正是"一把先射、另两把苦等一段时间"的根因。
        if (p == Phase.BLAST || p == Phase.BLAST_DELAY || p == Phase.SPIN) return true;
        if (p != Phase.BLAST_READY) return false;
        // 用锁定站位（未锁定则实时算一次）；阈值 0.6 避免阻尼追踪尾段卡在判定之外
        Vec3 target = blastReadyPos != null ? blastReadyPos : computeBlastPosition(owner, slotIndex);
        return closeTo(target, 0.6);
    }

    // 冲击波
    private void startBlast(LivingEntity owner) {
        phase = Phase.BLAST;
        phaseTick = 0;
        // ⚠️ 此处不能清空 blastReadyPos：BLAST/BLAST_DELAY 阶段还要靠它悬停在齐射站位。
        // 站位锁定在 SPIN 结束（attackIndex 归零）时释放，下一轮重新锁定。

        // 发射方向：目标存活时按当前站位（身后偏左）实时对准 ——
        // 不用 CHARGE 阶段在编队位算的 blastLockedDirection（剑已飞到站位，旧方向会射偏导致打不中）
        Vec3 lookDir;
        if (currentTarget != null && currentTarget.isAlive()) {
            Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
            lookDir = tc.subtract(position()).normalize();
        } else {
            lookDir = getTargetDirection();
            if (lookDir.lengthSqr() < 0.0001) {
                lookDir = Vec3.directionFromRotation(getXRot(), getYRot());
            }
        }

        Vec3 startPos = position().add(0, 0.3, 0);
        Vec3 endPos = startPos.add(lookDir.scale(range));

        // 发射光束前，剑尖对准发射方向（startBlast 此前不设置朝向，剑可能指向别处）
        if (lookDir.lengthSqr() > 1e-6) faceDirection(lookDir, 1.0);

        var blockHit = level().clip(new ClipContext(startPos, endPos,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 hitLoc = blockHit.getType() == HitResult.Type.MISS
            ? endPos : blockHit.getLocation();

        float[] tint = BLAST_TINTS[slotIndex];
        LizhiYanBlastVisualEntity blastEntity = new LizhiYanBlastVisualEntity(
            EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
            level(), startPos, hitLoc, lookDir, tint
        );
        level().addFreshEntity(blastEntity);

        LivingEntity hitEntity = findEntityOnPath(owner, startPos, hitLoc);
        if (hitEntity != null) {
            float damage = baseDamage * BLAST_DAMAGE_MULTIPLIER;
            var spell = SpellRegistry.LIZHI_YAN.get();
            SpellDamageSource source = SpellDamageSource.source(this, owner, spell).setIFrames(0);
            DamageSources.applyDamage(hitEntity, damage, source);
            //spawnBlastHitParticles(hitEntity.position().add(0, hitEntity.getBbHeight()/2, 0));
        }

        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @Nullable
    private LivingEntity findEntityOnPath(LivingEntity owner, Vec3 start, Vec3 end) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        AABB scanBox = new AABB(start, end).inflate(1.0);
        var entities = level().getEntities(this, scanBox, e -> {
            if (e == this || e == owner) return false;
            if (!(e instanceof LivingEntity living)) return false;
            if (e instanceof net.minecraft.world.entity.player.Player player
                && (currentTarget == null || !player.getUUID().equals(currentTarget.getUUID()))) {
                return false;
            }
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        for (Entity e : entities) {
            AABB entityBox = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> intercept = entityBox.clip(start, end);
            if (intercept.isPresent()) {
                double dist = start.distanceTo(intercept.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = (LivingEntity) e;
                }
            }
        }
        return closest;
    }

    private Vec3 getTargetDirection() {
        if (blastLockedDirection != null) return blastLockedDirection;
        if (currentTarget != null) {
            Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
            return tc.subtract(position()).normalize();
        }
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    // 状态同步
    private void syncStateToClient() {
        int phaseId = phase.ordinal();
        if (phaseId != entityData.get(ATTACK_PHASE)) {
            entityData.set(ATTACK_PHASE, phaseId);
        }
        entityData.set(AIM_YAW, currentYaw);
        entityData.set(AIM_PITCH, currentPitch);
    }

    // 工具方法

    private void spawnSummonParticles() {
        for (int i = 0; i < 10; i++) {
            level().addParticle(ParticleTypes.END_ROD,
                getX() + (random.nextDouble()-0.5)*0.4,
                getY() + (random.nextDouble()-0.5)*0.4,
                getZ() + (random.nextDouble()-0.5)*0.4,
                0, 0.05, 0);
        }
    }

    private void setInitialPosition(LivingEntity owner) {
        Vec3 offset = FORMATION_OFFSETS[slotIndex];
        float ownerYaw = owner.getYRot();
        double yawRad = ownerYaw * 0.0174533f;
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        double rx = offset.x * cos - offset.z * sin;
        double rz = offset.x * sin + offset.z * cos;
        setPos(owner.getX() + rx, owner.getY() + offset.y, owner.getZ() + rz);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            onAntiMagic(null); return true;
        }
        return false;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) onRemovedHelper(this);
        if (level().isClientSide) {
            for (int i = 0; i < 12; i++) {
                level().addParticle(ParticleTypes.END_ROD,
                    getX() + (random.nextDouble()-0.5),
                    getY() + (random.nextDouble()-0.5),
                    getZ() + (random.nextDouble()-0.5),
                    (random.nextDouble()-0.5)*0.1, 0.05, (random.nextDouble()-0.5)*0.1);
            }
        }
        super.remove(reason);
    }
}

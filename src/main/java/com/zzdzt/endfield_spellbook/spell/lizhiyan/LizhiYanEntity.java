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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    // 方向变化阈值：小于此值时跳过三角函数
    private static final double AIM_DIR_THRESHOLD_SQ = 0.001;
    // 位置更新降频：每 2 tick 计算一次
    private static final int POSITION_UPDATE_INTERVAL = 2;

    //攻击时序
    static final int CHARGE_TICKS_STAB = 10;
    static final int COOLDOWN_TICKS_STAB = 10;
    static final int COOLDOWN_TICKS_BLAST = 40;
    private static final float[] STAB_DAMAGE_MULTIPLIERS = {1.0f, 1.3f};
    private static final float BLAST_DAMAGE_MULTIPLIER = 3.0f;
    private static final int RETURN_TICKS = 10;
    // 冲刺硬超时兜底（tick）：目标乱跑追不上时放弃穿刺，防止相位机卡死
    static final int STAB_MAX_TICKS = 60;
    // 协同光束等待超时（tick）：三剑都完成前两段穿刺后，等它们全部就绪再齐射；超时强制放行
    static final int BLAST_READY_TIMEOUT = 60;
    // 三剑全部就位后的聚气停留（tick）：给三剑对齐时间 + 蓄力观感
    static final int BLAST_CHARGE_TICKS = 6;

    //旋转动作时序
    private static final int DEFAULT_SPIN_TICKS = 50;
    private static final float SPIN_RADIUS = 1.5f;
    private static final float SPIN_HEIGHT = 1.2f;
    private static final float SPIN_CENTER_DISTANCE = 2.0f;
    static final int BLAST_TO_SPIN_DELAY = 4;
    private int spinTicks = DEFAULT_SPIN_TICKS;

    //运行时状态
    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;
    private int slotIndex;
    private float baseDamage;
    private float range = 20.0f;

    // 目标获取与丢失视线判定独立维护，实体类负责消费目标并驱动攻击状态机。
    private final LizhiYanTargeting targeting = new LizhiYanTargeting(this);
    private final LizhiYanMovementController movement = new LizhiYanMovementController(this);
    private final LizhiYanAttackController attackController = new LizhiYanAttackController(this);


    // 位置缓存
    private Vec3 formationPos = Vec3.ZERO;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private float lastOwnerYaw = 0f;
    private double lastCosYaw = 1.0, lastSinYaw = 0.0;
    private int lastFormationComputeTick = -1;

    // 穿刺子状态与计时由专用控制器持有，实体只负责主攻击阶段与生命周期。
    private final LizhiYanThrustController thrustController = new LizhiYanThrustController(this);

    private int returnTick = 0;

    // 齐射站位的实际移动由实体执行，站位锁定状态由攻击控制器持有。

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
    
    /** Narrow package-level bridges used by the attack-state controller. */
    LizhiYanTargeting targetingController() { return targeting; }
    LizhiYanEntity.Phase getAttackPhaseState() { return attackController.phase(); }
    int getAttackPhaseTick() { return attackController.phaseTick(); }
    int getAttackIndex() { return attackController.attackIndex(); }
    void setAttackPhaseState(Phase value) { attackController.setPhase(value); }
    void setAttackPhaseTick(int value) { attackController.setPhaseTick(value); }
    void setAttackIndex(int value) { attackController.setAttackIndex(value); }
    void incrementAttackIndex() { attackController.incrementAttackIndex(); }
    @Nullable Vec3 getBlastLockedDirection() { return attackController.blastLockedDirection(); }
    void setBlastLockedDirection(@Nullable Vec3 value) { attackController.setBlastLockedDirection(value); }
    @Nullable Vec3 getBlastReadyPos() { return attackController.blastReadyPos(); }
    void setBlastReadyPos(@Nullable Vec3 value) { attackController.setBlastReadyPos(value); }
    int getBlastReadyHold() { return attackController.blastReadyHold(); }
    void incrementBlastReadyHold() { attackController.incrementBlastReadyHold(); }
    void resetBlastReadyHold() { attackController.resetBlastReadyHold(); }
    int getSpinTicksForController() { return spinTicks; }
    void setChargeScaleSynced(float value) { entityData.set(CHARGE_SCALE, value); }
    void resetThrustDamageState() { thrustController.resetDamageState(); }
    void forceFinishThrust() { thrustController.forceFinish(); }


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
        if (tag.contains("AttackIndex")) setAttackIndex(tag.getInt("AttackIndex"));
    }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Slot", slotIndex);
        tag.putFloat("BaseDamage", baseDamage);
        tag.putFloat("Range", range);
        tag.putInt("AttackIndex", getAttackIndex());
    }

    /** Movement-controller bridge for Entity's protected impulse flag. */
    void markMovementImpulse() { hasImpulse = true; }

    /** Movement-controller bridge: rotation remains owned by the entity. */
    void applyMovementRotation(float yaw, float pitch) {
        currentYaw = yaw;
        currentPitch = pitch;
        setYRot(yaw);
        setXRot(pitch);
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
        if (thrustController.isActive()) { thrustController.tick(); }
        else if (getAttackPhaseState() == Phase.SPIN) { updateSpin(owner); }
        else if (getAttackPhaseState() == Phase.BLAST_READY) { updateBlastReadyPosition(owner); }
        else if (getAttackPhaseState() == Phase.BLAST || getAttackPhaseState() == Phase.BLAST_DELAY) { holdBlastPosition(owner); }
        else { updateFormationPosition(owner); }

        targeting.update(owner, range);
        attackController.tick(owner);
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
    void updateAimDirection(LivingEntity owner) {
        Entity targetToAim = targeting.getCurrentTarget() != null ? targeting.getCurrentTarget() : targeting.getPendingTarget();

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

    // 攻击状态机
    // 戳刺（阻尼追踪相位机：后顿 → 冲刺 → 惯性滑行，物理时长，参照 YujianCraft flyToward）
    // 穿刺细分状态交由专用控制器；实体继续编排主攻击阶段。
    void startStab() {
        thrustController.start();
    }

    /** 控制器回调：进入 STAB 主阶段。 */
    void beginThrustPhase() {
        setAttackPhaseState(Phase.STAB);
        setAttackPhaseTick(0);
    }

    void setThrustingSynced(boolean value) {
        entityData.set(IS_THRUSTING, value);
    }

    /** 穿刺结束后沿用原有回编队 / 协同齐射转换逻辑。 */
    void onThrustFinished() {
        incrementAttackIndex();
        if (getAttackIndex() >= 2) {
            setAttackPhaseState(Phase.BLAST_READY);
            setAttackPhaseTick(0);
            resetBlastReadyHold();
            returnTick = 0;
        } else {
            returnTick = RETURN_TICKS;
            setAttackPhaseState(Phase.COOLDOWN);
            setAttackPhaseTick(0);
        }
    }

    void returnToIdleAfterMissingThrustTarget() {
        setAttackPhaseState(Phase.IDLE);
        setAttackPhaseTick(0);
    }

    @Nullable
    LivingEntity currentThrustTarget() {
        Entity target = targeting.getCurrentTarget();
        return target instanceof LivingEntity living ? living : null;
    }

    void playThrustStartSound() {
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3f, 1.5f);
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

    // 阻尼运动与朝向计算委托给专用控制器；攻击相位仍由实体持有。
    void flyToward(Vec3 destination, double steering, double maxSpeed) {
        movement.flyToward(destination, steering, maxSpeed);
    }

    void flyToward(Vec3 destination, double steering, double maxSpeed, @Nullable Vec3 facing) {
        movement.flyToward(destination, steering, maxSpeed, facing);
    }

    void flyToward(Vec3 destination, double steering, double maxSpeed,
                   @Nullable Vec3 facing, double turnRate) {
        movement.flyToward(destination, steering, maxSpeed, facing, turnRate);
    }

    private void faceDirection(Vec3 requested, double turnRate) {
        movement.faceDirection(requested, turnRate);
    }

    /** Package bridge for the blast executor to align the sword immediately before firing. */
    void faceDirectionForBlast(Vec3 requested) {
        faceDirection(requested, 1.0);
    }

    boolean closeTo(Vec3 point, double radius) {
        return movement.closeTo(point, radius);
    }

    private void updateSpin(LivingEntity owner) {
        double progress = getAttackPhaseTick() / (double) spinTicks;

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

    void applyStabDamage(LivingEntity target) {
        LivingEntity owner = getOwner();
        if (owner == null) return;
        float multiplier = STAB_DAMAGE_MULTIPLIERS[
            Math.min(getAttackIndex(), STAB_DAMAGE_MULTIPLIERS.length - 1)];
        float damage = baseDamage * multiplier;
        // 官方模式（EchoingArrowProjectile）：直接从 registry 取法术引用构造伤害来源
        var spell = SpellRegistry.LIZHI_YAN.get();
        SpellDamageSource source = SpellDamageSource.source(this, owner, spell).setIFrames(5);
        DamageSources.applyDamage(target, damage, source);
    }

    // 协同光束：进入归位等待态（三剑都就绪后一起 startBlast）
    void startBlastReady() {
        setAttackPhaseState(Phase.BLAST_READY);
        setAttackPhaseTick(0);
        resetBlastReadyHold();
        entityData.set(CHARGE_SCALE, 1.0f);
    }

    /** 同 owner 的其他飞剑（存活）。用于协同光束 / SPIN 屏障。 */
    private List<LizhiYanEntity> getSiblingSwords() {
        if (ownerUUID == null || !(level() instanceof ServerLevel)) return List.of();
        return level().getEntitiesOfClass(LizhiYanEntity.class,
                getBoundingBox().inflate(96), e -> e != this && ownerUUID.equals(e.ownerUUID));
    }


    /** 仅在初始索敌 / 首轮蓄力窗口内允许同步整组目标。 */
    boolean isInitialTargetAcquisitionWindow() {
        return getAttackIndex() == 0
            && (getAttackPhaseState() == Phase.IDLE || (getAttackPhaseState() == Phase.CHARGE && getAttackPhaseTick() <= CHARGE_TICKS_STAB));
    }

    /** Targeting controller bridge: same-owner sword group. */
    List<LizhiYanEntity> getSiblingSwordsForTargeting() {
        return getSiblingSwords();
    }

    /** Targeting controller bridge: share target state without duplicating ownership. */
    LizhiYanTargeting getTargetingForCoordination() {
        return targeting;
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
        Vec3 lockedPos = getBlastReadyPos();
        if (lockedPos == null) {
            lockedPos = computeBlastPosition(owner, slotIndex);
            setBlastReadyPos(lockedPos);
        }
        flyToward(lockedPos, 0.30, 1.0, aimFacing(owner));
    }

    /** 齐射后悬停在锁定站位（蓄力/后摇），保持三剑阵型与瞄准，直到 SPIN 把剑拉回身后环。 */
    private void holdBlastPosition(LivingEntity owner) {
        Vec3 lockedPos = getBlastReadyPos();
        if (lockedPos == null) return;
        flyToward(lockedPos, 0.25, 0.6, aimFacing(owner));
    }

    /** 剑尖朝向：目标存活时朝目标（0.55 身高），否则朝玩家前方。 */
    private Vec3 aimFacing(LivingEntity owner) {
        if (targeting.getCurrentTarget() != null && targeting.getCurrentTarget().isAlive()) {
            return targeting.getCurrentTarget().position().add(0, targeting.getCurrentTarget().getBbHeight() / 2, 0)
                    .subtract(position()).normalize();
        }
        float yawRad = owner.getYRot() * 0.0174533f;
        return new Vec3((float) -Math.sin(yawRad), 0, (float) -Math.cos(yawRad));
    }

    /**
     * 协同光束屏障（严格版）：所有剑 phase 在 blast 序列 + 各自 closeTo 自己的锁定站位，
     * 才一起齐射。仅靠 phase 序列不够——phase 进入早但飞行未到位就齐射会出现"还在编队位就开枪"。
     */
    boolean allAtBlastPositions(LivingEntity owner) {
        for (LizhiYanEntity s : getSiblingSwords()) {
            if (!s.swordReadyAndAtPos(owner)) return false;
        }
        return swordReadyAndAtPos(owner);
    }

    private boolean swordReadyAndAtPos(LivingEntity owner) {
        Phase p = getAttackPhaseState();
        // 已发射 / 后摇中的剑（BLAST、BLAST_DELAY、SPIN）：不再做就位判定。
        // ⚠️ 关键：startBlast 后剑会离开齐射站位，若仍要求 closeTo，其余剑会判定它"未就位"
        // 而一直卡到超时 —— 这正是"一把先射、另两把苦等一段时间"的根因。
        if (p == Phase.BLAST || p == Phase.BLAST_DELAY || p == Phase.SPIN) return true;
        if (p != Phase.BLAST_READY) return false;
        // 用锁定站位（未锁定则实时算一次）；阈值 0.6 避免阻尼追踪尾段卡在判定之外
        Vec3 lockedPos = getBlastReadyPos();
        Vec3 target = lockedPos != null ? lockedPos : computeBlastPosition(owner, slotIndex);
        return closeTo(target, 0.6);
    }

    // 冲击波
    void startBlast(LivingEntity owner) {
        setAttackPhaseState(Phase.BLAST);
        setAttackPhaseTick(0);
        // ⚠️ 此处不能清空齐射站位锁：BLAST/BLAST_DELAY 阶段还要靠它悬停在齐射站位。
        // 站位锁定在 SPIN 结束（attackIndex 归零）时释放，下一轮重新锁定。

        // Executor resolves the live target direction from the sword's current firing position.
        LizhiYanBlastExecutor.fire(
            this, owner, range, BLAST_TINTS[slotIndex],
            baseDamage * BLAST_DAMAGE_MULTIPLIER, targeting.getCurrentTarget(),
            getTargetDirection()
        );
    }

    private Vec3 getTargetDirection() {
        if (getBlastLockedDirection() != null) return getBlastLockedDirection();
        if (targeting.getCurrentTarget() != null) {
            Vec3 tc = targeting.getCurrentTarget().position().add(0, targeting.getCurrentTarget().getBbHeight()/2, 0);
            return tc.subtract(position()).normalize();
        }
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    // 状态同步
    private void syncStateToClient() {
        int phaseId = getAttackPhaseState().ordinal();
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

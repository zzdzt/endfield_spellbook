package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.entity.CasterOwnedEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

    private static final float[][] BLAST_TINTS = {
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f}
    };

    //攻击时序
    static final int CHARGE_TICKS_STAB = 10;
    static final int COOLDOWN_TICKS_STAB = 10;
    static final int COOLDOWN_TICKS_BLAST = 40;
    private static final float BLAST_DAMAGE_MULTIPLIER = 3.0f;
    // 冲刺硬超时兜底（tick）：目标乱跑追不上时放弃穿刺，防止相位机卡死
    static final int STAB_MAX_TICKS = 60;
    // 协同光束等待超时（tick）：三剑都完成前两段穿刺后，等它们全部就绪再齐射；超时强制放行
    static final int BLAST_READY_TIMEOUT = 60;
    // 三剑全部就位后的聚气停留（tick）：给三剑对齐时间 + 蓄力观感
    static final int BLAST_CHARGE_TICKS = 6;

    //旋转动作时序
    private static final int DEFAULT_SPIN_TICKS = 50;
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
    private final LizhiYanFormationController formation = new LizhiYanFormationController(this);
    private final LizhiYanPresentationController presentation = new LizhiYanPresentationController(this);
    private final LizhiYanAttackController attackController = new LizhiYanAttackController(this);


    // 穿刺子状态与计时由专用控制器持有，实体只负责主攻击阶段与生命周期。
    private final LizhiYanThrustController thrustController = new LizhiYanThrustController(this);

    // 齐射站位的实际移动由实体执行，站位锁定状态由攻击控制器持有。

    // 方向缓存
    private float currentYaw = 0f;
    private float currentPitch = 0f;

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

        Vec3 aim = LizhiYanFormationController.defaultAim(slot);
        this.currentYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        this.currentPitch = (float) -Math.toDegrees(
            Math.atan2(aim.y, Math.sqrt(aim.x*aim.x + aim.z*aim.z)));

        formation.initializePosition(owner);
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
    float getBaseDamage() { return baseDamage; }
    float currentAimYaw() { return currentYaw; }
    float currentAimPitch() { return currentPitch; }
    void refreshAimRotationHistory() { setYRot(currentYaw); setXRot(currentPitch); yRotO=currentYaw; xRotO=currentPitch; }
    void applyAimRotation(float yaw,float pitch) { currentYaw=yaw; currentPitch=pitch; setYRot(yaw); setXRot(pitch); yRotO=yaw; xRotO=pitch; }
    boolean isFirstTickForPresentation() { return firstTick; }
    void updateAimDirection(LivingEntity owner) { formation.updateAimDirection(owner); }
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
        presentation.tickClient(isFirstTickForPresentation(),Phase.values()[entityData.get(ATTACK_PHASE)],entityData.get(CHARGE_SCALE),entityData.get(IS_THRUSTING));
    }

    private void tickServer() {
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { onUnSummon(); return; }

        if (formation.isReturning()) { formation.updateReturnToFormation(owner); syncStateToClient(); return; }
        if (thrustController.isActive()) { thrustController.tick(); }
        else if (getAttackPhaseState() == Phase.SPIN) { formation.updateSpin(owner,getAttackPhaseTick(),spinTicks); }
        else if (getAttackPhaseState() == Phase.BLAST_READY) { formation.updateBlastReadyPosition(owner); }
        else if (getAttackPhaseState() == Phase.BLAST || getAttackPhaseState() == Phase.BLAST_DELAY) { formation.holdBlastPosition(owner); }
        else { formation.updateFormationPosition(owner); }

        targeting.update(owner, range);
        attackController.tick(owner);
        syncStateToClient();
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
            formation.cancelReturn();
        } else {
            formation.beginReturn();
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

    void applyStabDamage(LivingEntity target) { LizhiYanCombatExecutor.applyStabDamage(this,target); }

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
     * Coordinated-blast barrier: every sibling must be in the blast sequence and at its
     * own locked firing position before the volley begins.
     */
    boolean allAtBlastPositions(LivingEntity owner) {
        for (LizhiYanEntity sword : getSiblingSwords()) {
            if (!sword.swordReadyAndAtPos(owner)) return false;
        }
        return swordReadyAndAtPos(owner);
    }

    /** Formation-controller bridge: whether this sword has reached its locked firing position. */
    boolean swordReadyAndAtPos(LivingEntity owner) {
        return formation.swordReadyAndAtPos(owner);
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
            presentation.spawnRemovalParticles();
        }
        super.remove(reason);
    }
}

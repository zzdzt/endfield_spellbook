package com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.effect.LiquidNitrogenMarkedEffect;
import com.zzdzt.endfield_spellbook.entity.CasterOwnedEntity;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 压缩液氮炮弹（温蒂 S3「液氮大炮」对齐）。
 *
 * 弹道机制：
 *   - 低平直线飞行，命中第一个敌人（扫掠判定）或方块，或飞满最大射程后在空中爆
 *   - 爆炸：以着弹点为圆心的圆形范围法术伤害 + 极强水平击退（受击退抗性衰减）
 *     + 液氮标记（8 秒内移动受正比于移动距离的伤害）
 *
 * 演出阶段（PHASE 同步）：
 *   0 FLYING  —— 弹体飞行（渲染器画球体 + 冷雾拖尾，客户端自发尾迹粒子）
 *   1 IMPACT  —— 命中演出（渲染器画贴地扩散环 + 爆散闪光，RING_TICKS 后 discard）
 */
public class LncProjectileEntity extends Entity implements IEntityAdditionalSpawnData, CasterOwnedEntity {

    public static final int PHASE_FLYING = 0;
    public static final int PHASE_IMPACT = 1;

    /** 飞行速度（格/tick） */
    public static final double SPEED = 1.1;
    /** 命中演出时长（tick）：扩散环 + 闪光 */
    public static final int RING_TICKS = 10;

    private static final EntityDataAccessor<Integer> PHASE =
        SynchedEntityData.defineId(LncProjectileEntity.class, EntityDataSerializers.INT);

    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;

    private float damage;
    private int spellLevel;
    private float powerMult;
    private float knockback;
    private float explosionRadius;
    private float maxRange;

    private Vec3 direction = Vec3.ZERO;
    private double traveled;
    private int impactAge;

    public LncProjectileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public LncProjectileEntity(EntityType<?> type, Level level, LivingEntity owner,
                               Vec3 origin, Vec3 direction,
                               float damage, int spellLevel, float powerMult,
                               float knockback, float explosionRadius, float maxRange) {
        this(type, level);
        this.ownerUUID = owner.getUUID();
        this.ownerCached = owner;
        this.damage = damage;
        this.spellLevel = spellLevel;
        this.powerMult = powerMult;
        this.knockback = knockback;
        this.explosionRadius = explosionRadius;
        this.maxRange = maxRange;
        this.direction = direction.normalize();
        this.setPos(origin);
        setRotationFromDirection(this.direction);
    }

    private void setRotationFromDirection(Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(
            Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    // ===== 同步数据 =====

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHASE, PHASE_FLYING);
    }

    public int getPhase() {
        return entityData.get(PHASE);
    }

    /** 拖尾/扩散环冷雾色（白核 → 冰蓝）。 */
    public static float[] tintCore() {
        return new float[]{0.95f, 0.99f, 1.00f};
    }

    public static float[] tintShell() {
        return new float[]{0.55f, 0.85f, 1.00f};
    }

    public float getExplosionRadius() {
        return explosionRadius;
    }

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

    // ===== 主循环 =====

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            tickClient();
            return;
        }

        if (entityData.get(PHASE) == PHASE_IMPACT) {
            if (++impactAge >= RING_TICKS) {
                discard();
            }
            return;
        }

        tickFlight();
    }

    private void tickFlight() {
        Vec3 prev = position();
        Vec3 next = prev.add(direction.scale(SPEED));
        traveled += SPEED;

        // ① 敌人扫掠：路径 AABB 内取沿路径最早相交的活体
        LivingEntity hitEntity = findEntityOnPath(prev, next);
        if (hitEntity != null) {
            Vec3 hitPos = hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0);
            impact(hitPos);
            return;
        }

        // ② 方块拦截
        BlockHitResult blockHit = level().clip(new ClipContext(prev, next,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            impact(blockHit.getLocation());
            return;
        }

        // ③ 飞满射程：空中爆（原作：范围内没有敌人也能发射）
        if (traveled >= maxRange) {
            impact(next);
            return;
        }

        setPos(next);
    }

    @Nullable
    private LivingEntity findEntityOnPath(Vec3 prev, Vec3 next) {
        LivingEntity owner = getOwner();
        AABB pathBox = new AABB(prev, next).inflate(0.4);
        var candidates = level().getEntitiesOfClass(LivingEntity.class, pathBox, e -> {
            if (e.is(this) || e == owner) return false;
            if (!e.isAlive() || !e.isPickable()) return false;
            if (owner != null && DamageSources.isFriendlyFireBetween(owner, e)) return false;
            return true;
        });
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            var box = e.getBoundingBox().inflate(0.25);
            var intercept = box.clip(prev, next);
            if (intercept.isEmpty()) continue;
            double dist = prev.distanceTo(intercept.get());
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }

    /** 命中：切到演出阶段并结算圆形范围。 */
    private void impact(Vec3 hitPos) {
        setPos(hitPos);
        entityData.set(PHASE, PHASE_IMPACT);
        impactAge = 0;
        explode(hitPos);
    }

    private void explode(Vec3 center) {
        LivingEntity owner = getOwner();
        var damageSource = SpellRegistry.LIQUID_NITROGEN_CANNON.get().getDamageSource(this, owner);

        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
            AABB.ofSize(center, explosionRadius * 2, explosionRadius, explosionRadius * 2),
            e -> {
                if (e.is(this) || e == owner) return false;
                if (!e.isAlive() || !e.isPickable()) return false;
                if (e.distanceToSqr(center) >= explosionRadius * explosionRadius) return false;
                // 爆炸不穿墙：爆心到目标中心被方块截断（截断点离目标尚远）则跳过
                var clip = level().clip(new ClipContext(center,
                    e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
                if (clip.getType() != HitResult.Type.MISS
                    && clip.getLocation().distanceTo(e.getBoundingBox().getCenter()) > 0.75) {
                    return false;
                }
                return owner == null || !DamageSources.isFriendlyFireBetween(owner, e);
            });

        for (LivingEntity target : targets) {
            float distFactor = 1.0f - (float) (target.position().distanceTo(center) / explosionRadius);
            float falloff = 0.7f + 0.3f * distFactor; // 边缘轻微衰减
            DamageSources.applyDamage(target, damage * falloff, damageSource);

            // 极强水平击退（受击退抗性衰减，方向 = 爆心 → 目标）
            Vec3 dir = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z);
            if (dir.lengthSqr() < 0.001) {
                dir = new Vec3(direction.x, 0, direction.z);
            }
            dir = dir.normalize();
            float kbFactor = Utils.clampedKnockbackResistanceFactor(target, 0.1f, 1.0f);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(knockback * kbFactor)));
            target.hurtMarked = true;

            // 液氮标记：8 秒内移动受正比于移动距离的伤害
            LiquidNitrogenMarkedEffect.storeCasterInfo(target, owner != null ? owner : target, powerMult);
            target.addEffect(new MobEffectInstance(
                com.zzdzt.endfield_spellbook.registry.EffectRegistry.LIQUID_NITROGEN_MARKED.get(),
                LiquidNitrogenMarkedEffect.EFFECT_DURATION, spellLevel - 1,
                false, false, true));

            // 目标命中冰晶
            MagicManager.spawnParticles(level(), ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + target.getBbHeight() * 0.5f, target.getZ(),
                12, target.getBbWidth() * 0.4f, target.getBbHeight() * 0.4f, target.getBbWidth() * 0.4f,
                0.02, false);
        }

        // 爆散粒子（低亮度）：环形冲击波（ISS Blastwave，白蓝）+ 白色水花飞溅 + 冰雾
        var tint = new Vector3f(0.42f, 0.68f, 0.92f);
        MagicManager.spawnParticles(level(),
            new io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions(tint, explosionRadius * 0.3f),
            center.x, center.y, center.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level(),
            new io.redspace.ironsspellbooks.particle.SparkParticleOptions(tint),
            center.x, center.y, center.z, 16, 0.2f, 0.2f, 0.2f, 0.16f, true);
        MagicManager.spawnParticles(level(), ParticleTypes.CLOUD,
            center.x, center.y, center.z, 10,
            explosionRadius * 0.25f, explosionRadius * 0.2f, explosionRadius * 0.25f,
            0.008, false);

        // 音效：爆炸 + 液氮汽化"呲"
        level().playSound(null, center.x, center.y, center.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.25f);
        level().playSound(null, center.x, center.y, center.z,
            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.8f);
    }

    // ===== 客户端尾迹 =====

    private void tickClient() {
        if (entityData.get(PHASE) != PHASE_FLYING) return;
        // 冷雾尾迹：白蓝尘 + 冰晶（低亮度）
        var tint = new Vector3f(0.48f, 0.74f, 0.95f);
        level().addParticle(
            new net.minecraft.core.particles.DustParticleOptions(tint, 0.40f),
            getX() + (random.nextDouble() - 0.5) * 0.15,
            getY() + (random.nextDouble() - 0.5) * 0.15,
            getZ() + (random.nextDouble() - 0.5) * 0.15,
            0, 0.005, 0);
        if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE,
                getX(), getY(), getZ(), 0, 0.01, 0);
        }
    }

    // ===== 存档（瞬时弹不存档）=====

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeFloat(damage);
        buffer.writeInt(spellLevel);
        buffer.writeFloat(powerMult);
        buffer.writeFloat(knockback);
        buffer.writeFloat(explosionRadius);
        buffer.writeFloat(maxRange);
        buffer.writeDouble(direction.x);
        buffer.writeDouble(direction.y);
        buffer.writeDouble(direction.z);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        damage = buffer.readFloat();
        spellLevel = buffer.readInt();
        powerMult = buffer.readFloat();
        knockback = buffer.readFloat();
        explosionRadius = buffer.readFloat();
        maxRange = buffer.readFloat();
        double dx = buffer.readDouble();
        double dy = buffer.readDouble();
        double dz = buffer.readDouble();
        direction = new Vec3(dx, dy, dz);
        setRotationFromDirection(direction);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

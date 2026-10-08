package com.zzdzt.endfield_spellbook.spell.gloompurge;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * 破晦阵几何印记实体
 */
public class GloompurgeMarkEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    public static final int SHAPE_CHARGE_TRIANGLE = 0;
    public static final int SHAPE_DOMAIN_OCTAGON = 1;
    // 领域边缘装饰环：反向虚线弧 + 径向刻度，半径 = 领域半径
    public static final int SHAPE_DOMAIN_EDGE = 2;

    private static final EntityDataAccessor<Integer> SHAPE =
        SynchedEntityData.defineId(GloompurgeMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
        SynchedEntityData.defineId(GloompurgeMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROT =
        SynchedEntityData.defineId(GloompurgeMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(GloompurgeMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FADING =
        SynchedEntityData.defineId(GloompurgeMarkEntity.class, EntityDataSerializers.BOOLEAN);

    public GloompurgeMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GloompurgeMarkEntity(EntityType<?> type, Level level, Vec3 pos,
                                int shape, float radius, float rot, int lifetime) {
        this(type, level);
        this.setPos(pos);
        this.entityData.set(SHAPE, shape);
        this.entityData.set(RADIUS, radius);
        this.entityData.set(ROT, rot);
        this.entityData.set(LIFETIME, lifetime);
    }

    @Override
    public void tick() {
        this.baseTick();
        // 与 TargetedAreaEntity 同款：尾 10 tick 淡出，由服务端同步驱动
        if (!level().isClientSide && tickCount >= getLifetime() - 10) {
            this.entityData.set(FADING, true);
        }
        if (tickCount > getLifetime()) {
            discard();
            return;
        }
        // 边界印记：沿光墙周向喷发上浮光尘
        if (level().isClientSide && getShape() == SHAPE_DOMAIN_EDGE && tickCount % 2 == 0) {
            float radius = getRadius();
            double ang = level().random.nextDouble() * Mth.TWO_PI;
            double px = getX() + Math.cos(ang) * radius;
            double pz = getZ() + Math.sin(ang) * radius;
            double py = getY() + level().random.nextDouble() * radius * 0.25;
            level().addParticle(
                new DustParticleOptions(new Vector3f(0.35f, 0.8f, 1.0f), 0.9f),
                px, py, pz, 0, 0.02, 0);
        }
    }

    // 同步数据

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SHAPE, SHAPE_CHARGE_TRIANGLE);
        this.entityData.define(RADIUS, 1f);
        this.entityData.define(ROT, 0f);
        this.entityData.define(LIFETIME, 20);
        this.entityData.define(FADING, false);
    }

    public int getShape() {
        return entityData.get(SHAPE);
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public float getRot() {
        return entityData.get(ROT);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public boolean isFading() {
        return entityData.get(FADING);
    }

    // 存档（不存档，仅同步）

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(SHAPE, tag.getInt("Shape"));
        this.entityData.set(RADIUS, tag.getFloat("Radius"));
        this.entityData.set(ROT, tag.getFloat("Rot"));
        this.entityData.set(LIFETIME, tag.getInt("Lifetime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Shape", getShape());
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Rot", getRot());
        tag.putInt("Lifetime", getLifetime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeInt(getShape());
        buffer.writeFloat(getRadius());
        buffer.writeFloat(getRot());
        buffer.writeInt(getLifetime());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.entityData.set(SHAPE, buffer.readInt());
        this.entityData.set(RADIUS, buffer.readFloat());
        this.entityData.set(ROT, buffer.readFloat());
        this.entityData.set(LIFETIME, buffer.readInt());
    }

    @Override
    public boolean shouldRender(double pX, double pY, double pZ) {
        return true;
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

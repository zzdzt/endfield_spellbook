package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * LizhiYan 冲击波视觉实体
 */
public class LizhiYanBlastVisualEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    public static final int LIFETIME = 10;

    // 方向数据
    private Vec3 blastDirection = Vec3.ZERO;
    public float distance;

    // 颜色色调
    private float[] tintColor = {1.0f, 1.0f, 1.0f};

    public LizhiYanBlastVisualEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }
    /**
     * 便捷构造函数（实体类型由外部传入）
     */
    public LizhiYanBlastVisualEntity(EntityType<LizhiYanBlastVisualEntity> entityType,
                                      Level level, Vec3 start, Vec3 end,
                                      Vec3 direction, float[] tint) {
        super(entityType, level);
        this.setPos(start);
        this.distance = (float) start.distanceTo(end);
        this.blastDirection = direction.normalize();
        this.tintColor = tint != null ? tint : new float[]{1.0f, 1.0f, 1.0f};
        setRotationFromDirection(this.blastDirection);
    }

    private void setRotationFromDirection(Vec3 dir) {
        double dx = dir.x;
        double dy = dir.y;
        double dz = dir.z;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(
            Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))
        );

        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    @Override
    public void tick() {
        if (tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Distance")) this.distance = tag.getFloat("Distance");
        if (tag.contains("DirX")) {
            this.blastDirection = new Vec3(
                tag.getDouble("DirX"),
                tag.getDouble("DirY"),
                tag.getDouble("DirZ")
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Distance", this.distance);
        if (this.blastDirection != null) {
            tag.putDouble("DirX", this.blastDirection.x);
            tag.putDouble("DirY", this.blastDirection.y);
            tag.putDouble("DirZ", this.blastDirection.z);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeFloat(distance);
        buffer.writeDouble(blastDirection.x);
        buffer.writeDouble(blastDirection.y);
        buffer.writeDouble(blastDirection.z);
        buffer.writeFloat(tintColor[0]);
        buffer.writeFloat(tintColor[1]);
        buffer.writeFloat(tintColor[2]);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.distance = buffer.readFloat();
        double dx = buffer.readDouble();
        double dy = buffer.readDouble();
        double dz = buffer.readDouble();
        this.blastDirection = new Vec3(dx, dy, dz);
        this.tintColor = new float[]{buffer.readFloat(), buffer.readFloat(), buffer.readFloat()};
        setRotationFromDirection(this.blastDirection);
    }

    @Override
    public boolean shouldRender(double pX, double pY, double pZ) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // 公共 Getter（重命名避免冲突）
    public Vec3 getBlastDirection() { return blastDirection; }
    public float[] getTintColor() { return tintColor; }
}

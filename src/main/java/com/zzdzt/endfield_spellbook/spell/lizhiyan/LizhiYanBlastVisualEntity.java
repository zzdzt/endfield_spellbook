package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * 项目内共用的光束视觉实体。
 *
 * <p>默认样式继续服务理之言/破晦阵；风矢使用独立的主束、螺旋环列与命中终结渲染，
 * 共用同一实体类型和 Forge 实体生成同步，不新增弹道网络协议。
 */
public class LizhiYanBlastVisualEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    public static final int LIFETIME = 10;

    public static final int STYLE_BLAST = 0;
    public static final int STYLE_WIND_ARROW = 1;
    public static final int STYLE_WIND_ARROW_HIT = 2;
    public static final int STYLE_WIND_ARROW_FINAL_HIT = 3;

    private static final EntityDataAccessor<Integer> DATA_VISUAL_STYLE =
        SynchedEntityData.defineId(LizhiYanBlastVisualEntity.class, EntityDataSerializers.INT);

    private Vec3 blastDirection = Vec3.ZERO;
    public float distance;
    private float[] tintColor = {1.0f, 1.0f, 1.0f};

    public LizhiYanBlastVisualEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /** 兼容旧调用：默认保持原有 blast 渲染样式。 */
    public LizhiYanBlastVisualEntity(EntityType<LizhiYanBlastVisualEntity> entityType,
                                      Level level, Vec3 start, Vec3 end,
                                      Vec3 direction, float[] tint) {
        this(entityType, level, start, end, direction, tint, STYLE_BLAST);
    }

    /** 创建指定视觉样式；样式可通过 SynchedEntityData 在生成后切换到终结爆发。 */
    public LizhiYanBlastVisualEntity(EntityType<LizhiYanBlastVisualEntity> entityType,
                                      Level level, Vec3 start, Vec3 end,
                                      Vec3 direction, float[] tint, int visualStyle) {
        super(entityType, level);
        this.setPos(start);
        this.distance = (float) start.distanceTo(end);
        Vec3 safeDirection = direction == null || direction.lengthSqr() < 1.0e-6
            ? new Vec3(0, 0, 1) : direction.normalize();
        this.blastDirection = safeDirection;
        this.tintColor = tint != null ? tint.clone() : new float[]{1.0f, 1.0f, 1.0f};
        this.entityData.set(DATA_VISUAL_STYLE, normalizeVisualStyle(visualStyle));
        setRotationFromDirection(this.blastDirection);
    }

    private static int normalizeVisualStyle(int style) {
        return style >= STYLE_BLAST && style <= STYLE_WIND_ARROW_FINAL_HIT
            ? style : STYLE_BLAST;
    }

    public int getVisualStyle() {
        return this.entityData.get(DATA_VISUAL_STYLE);
    }

    public void setVisualStyle(int visualStyle) {
        this.entityData.set(DATA_VISUAL_STYLE, normalizeVisualStyle(visualStyle));
    }

    public boolean isWindArrowStyle() {
        int style = getVisualStyle();
        return style >= STYLE_WIND_ARROW && style <= STYLE_WIND_ARROW_FINAL_HIT;
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
        // Entity.tick() 推进 tickCount；不调用它会令淡出动画和 LIFETIME 永远停在第 0 帧。
        super.tick();

        // 风矢的星点、连接余痕与弧带统一由客户端渲染器分层绘制。
        // 不再叠加默认 Dust 粒子，避免普通圆点覆盖星链并导致主束边缘发脏。

        if (tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VISUAL_STYLE, STYLE_BLAST);
    }

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
        if (tag.contains("VisualStyle")) setVisualStyle(tag.getInt("VisualStyle"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Distance", this.distance);
        if (this.blastDirection != null) {
            tag.putDouble("DirX", this.blastDirection.x);
            tag.putDouble("DirY", this.blastDirection.y);
            tag.putDouble("DirZ", this.blastDirection.z);
        }
        tag.putInt("VisualStyle", getVisualStyle());
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
        buffer.writeVarInt(getVisualStyle());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.distance = buffer.readFloat();
        double dx = buffer.readDouble();
        double dy = buffer.readDouble();
        double dz = buffer.readDouble();
        this.blastDirection = new Vec3(dx, dy, dz);
        this.tintColor = new float[]{buffer.readFloat(), buffer.readFloat(), buffer.readFloat()};
        setVisualStyle(buffer.readVarInt());
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

    public Vec3 getBlastDirection() {
        return blastDirection;
    }

    public float[] getTintColor() {
        return tintColor;
    }
}

package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 目标标记环（惊霆诀引导标记）：目标脚下的水墨色圆环。
 *
 * <p>固定于生成点（不跟随目标），40 tick：前 30t 亮流旋转，后 10t 线性淡出。
 * 渲染走 TargetMarkRenderer（FxGeometry.arc）。SpellVisualOnly。
 */
public class TargetMarkEntity extends Entity implements SpellVisualOnly {

    private static final EntityDataAccessor<Float> RADIUS =
        SynchedEntityData.defineId(TargetMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(TargetMarkEntity.class, EntityDataSerializers.INT);

    public TargetMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TargetMarkEntity(EntityType<?> type, Level level, Vec3 pos, float radius, int lifetime) {
        this(type, level);
        this.setPos(pos);
        this.entityData.set(RADIUS, radius);
        this.entityData.set(LIFETIME, lifetime);
    }

    @Override
    public void tick() {
        this.baseTick();
        if (!level().isClientSide && tickCount >= getLifetime()) {
            discard();
        }
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RADIUS, 1.0f);
        this.entityData.define(LIFETIME, 40);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // shouldBeSaved() = false
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // shouldBeSaved() = false
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
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

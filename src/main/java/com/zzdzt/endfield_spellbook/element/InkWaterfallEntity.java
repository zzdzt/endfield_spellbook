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
 * 水墨雷瀑（青霆剑诀首击/收尾的瀑布雷柱，移植自 ArcaneMag 惊霆诀定版 V2 演出结构）。
 *
 * <p>中央大瀑布（暗芯柱 + 垂落丝线）+ 副瀑布散布；tier 驱动副瀑布数量与红芯
 * （tier≥3 夹朱红芯）。SpellVisualOnly，不存档、无碰撞、固定位置。
 * 实体在雷击帧生成，本地时序：t0~1 过冲 → 呼吸 → t7~10 收束 → 销毁。
 */
public class InkWaterfallEntity extends Entity implements SpellVisualOnly {

    /** 本地寿命（tick）：过冲 1 + 呼吸 6 + 收束 3。 */
    public static final int PILLAR_LIFETIME = 10;

    private static final EntityDataAccessor<Float> HEIGHT =
        SynchedEntityData.defineId(InkWaterfallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPREAD =
        SynchedEntityData.defineId(InkWaterfallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TIER =
        SynchedEntityData.defineId(InkWaterfallEntity.class, EntityDataSerializers.INT);

    public InkWaterfallEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** 完整构造：柱顶高度 / 副瀑布散布半径 / 充能档（0~4，≥3 带红芯）。 */
    public InkWaterfallEntity(EntityType<?> type, Level level, Vec3 pos,
                              float height, float spread, int tier) {
        this(type, level);
        this.setPos(pos);
        this.entityData.set(HEIGHT, height);
        this.entityData.set(SPREAD, spread);
        this.entityData.set(TIER, tier);
    }

    @Override
    public void tick() {
        this.baseTick();
        if (!level().isClientSide && tickCount >= PILLAR_LIFETIME) {
            discard();
        }
    }

    // 渲染参数

    public float getHeight() {
        return entityData.get(HEIGHT);
    }

    public float getSpread() {
        return entityData.get(SPREAD);
    }

    public int getTier() {
        return entityData.get(TIER);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(HEIGHT, 24f);
        this.entityData.define(SPREAD, 2.0f);
        this.entityData.define(TIER, 0);
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

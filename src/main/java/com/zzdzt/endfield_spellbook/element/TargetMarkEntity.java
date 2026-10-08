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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 目标标记环（青霆剑诀引导标记）：目标脚下的水墨色圆环。
 *
 * <p>始终跟随主目标（服务端每 tick 同步目标位置，客户端经实体位置同步自然跟随）；
 * 目标死亡/移除即消散。40 tick：前 30t 亮流旋转，后 10t 线性淡出。
 * 渲染走 TargetMarkRenderer（贴图环 quad）。SpellVisualOnly。
 */
public class TargetMarkEntity extends Entity implements SpellVisualOnly {

    private static final EntityDataAccessor<Float> RADIUS =
        SynchedEntityData.defineId(TargetMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(TargetMarkEntity.class, EntityDataSerializers.INT);
    /** 跟随的主目标实体 id（-1 = 无）。 */
    private static final EntityDataAccessor<Integer> TARGET_ID =
        SynchedEntityData.defineId(TargetMarkEntity.class, EntityDataSerializers.INT);

    public TargetMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** 完整构造：跟随 target 的水墨印记。 */
    public TargetMarkEntity(EntityType<?> type, Level level, LivingEntity target, float radius, int lifetime) {
        this(type, level);
        this.setPos(target.position());
        this.entityData.set(RADIUS, radius);
        this.entityData.set(LIFETIME, lifetime);
        this.entityData.set(TARGET_ID, target.getId());
    }

    @Override
    public void tick() {
        this.baseTick();
        if (level().isClientSide) {
            return;
        }
        // 跟随主目标：目标死亡/移除即消散
        Entity target = level().getEntity(getTargetId());
        if (target == null || !target.isAlive()) {
            discard();
            return;
        }
        this.setPos(target.position());
        if (tickCount >= getLifetime()) {
            discard();
        }
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public int getTargetId() {
        return entityData.get(TARGET_ID);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RADIUS, 1.0f);
        this.entityData.define(LIFETIME, 40);
        this.entityData.define(TARGET_ID, -1);
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

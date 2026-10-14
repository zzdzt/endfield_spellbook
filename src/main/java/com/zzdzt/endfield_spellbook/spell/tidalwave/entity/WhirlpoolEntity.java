package com.zzdzt.endfield_spellbook.spell.tidalwave.entity;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import com.zzdzt.endfield_spellbook.spell.tidalwave.TidalWaveCastCurve;
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

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 涡流（踏潮卷浪场资源）：潮汐冲击命中敌人后在其位置生成的小型旋转水环。
 *
 * <p>职责仅限：世界位置 / 生命周期 / 所属玩家 / 可被消耗（存在即可被下次施法消耗，
 * 在被消耗的位置转换为水龙卷）。不负责伤害、寒冷附着、水龙卷逻辑。
 *
 * <p>模式对齐青霆剑（{@code SunderbladeEntity}）：纯视觉（{@link SpellVisualOnly}），
 * noPhysics、不存档、无碰撞；ownerId 走 Forge spawn 包同步。
 */
public class WhirlpoolEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(WhirlpoolEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;

    public WhirlpoolEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WhirlpoolEntity(EntityType<?> type, Level level, Vec3 pos, UUID ownerId) {
        this(type, level);
        this.setPos(pos);
        this.ownerId = ownerId;
        this.entityData.set(LIFETIME, TidalWaveCastCurve.WHIRLPOOL_LIFETIME_TICKS);
    }

    @Override
    public void tick() {
        this.baseTick();

        if (level().isClientSide) {
            // 寒气上浮（CRYO 色）
            if (tickCount % 4 == 0) {
                level().addParticle(new DustParticleOptions(
                        new Vector3f(0.55f, 0.80f, 1.0f), 0.6f),
                    getX(), getY() + 0.08, getZ(), 0, 0.02, 0);
            }
            return;
        }

        if (tickCount >= getLifetime()) {
            discard();
        }
    }

    /** 剩余寿命（tick）：渲染淡入淡出用。 */
    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFETIME, TidalWaveCastCurve.WHIRLPOOL_LIFETIME_TICKS);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // shouldBeSaved() = false，无存档数据
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // shouldBeSaved() = false，无存档数据
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeNullable(ownerId, FriendlyByteBuf::writeUUID);
        buffer.writeInt(entityData.get(LIFETIME));
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        ownerId = buffer.readNullable(FriendlyByteBuf::readUUID);
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

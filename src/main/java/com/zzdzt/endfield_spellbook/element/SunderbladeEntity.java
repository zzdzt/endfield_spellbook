package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
 * 青霆剑（庄方宜完整版惊霆诀的场资源）：雷炁凝形、插地固定的能量剑。
 *
 * <p>机制（拍板）：
 * - 不消耗——36 秒存场，可被惊霆诀反复引导（每次施放全场剑放电演出，伤害即时结算）
 * - 每位施法者上限 9 柄，环形插在目标脚下
 * - 引导演出：按 index 错峰，到点在目标位置引发一道水墨雷（复用 InkZap），剑体亮闪
 *
 * <p>纯视觉（{@link SpellVisualOnly}），不存档、无碰撞。放电目标由 targetId 查找。
 */
public class SunderbladeEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    /** 存场时长（tick）：36 秒。 */
    public static final int LIFETIME_TICKS = 20 * 36;

    /** 剑体渲染/粒子整体放大倍数（占位待调，用户指定 10x）。 */
    public static final float RENDER_SCALE = 7f;

    private static final EntityDataAccessor<Integer> INDEX =
        SynchedEntityData.defineId(SunderbladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(SunderbladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STRIKE_AT =
        SynchedEntityData.defineId(SunderbladeEntity.class, EntityDataSerializers.LONG);

    private UUID ownerId;
    private int targetEntityId;

    public SunderbladeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SunderbladeEntity(EntityType<?> type, Level level, Vec3 pos,
                             UUID ownerId, int targetEntityId, int index) {
        this(type, level);
        this.setPos(pos);
        this.setYRot(index * 40f);
        this.ownerId = ownerId;
        this.targetEntityId = targetEntityId;
        this.entityData.set(INDEX, index);
        this.entityData.set(LIFETIME, LIFETIME_TICKS);
        this.entityData.set(STRIKE_AT, Long.MAX_VALUE); // 未引导
    }

    /** 服务端：安排本次引导的放电时点（按 index 错峰）。 */
    public void markStrikeAt(long gameTime) {
        this.entityData.set(STRIKE_AT, gameTime);
    }

    @Override
    public void tick() {
        this.baseTick();

        if (level().isClientSide) {
            // 贴地青光（雷炁渗地）
            if (tickCount % 8 == 0) {
                level().addParticle(new DustParticleOptions(
                        new Vector3f(0.31f, 0.85f, 1.0f), 0.5f),
                    getX(), getY() + 0.05, getZ(), 0, 0, 0);
            }
            return;
        }

        // 服务端：倒计时消散（尾 10 tick 淡出由渲染器读 lifetime 处理）
        if (tickCount >= getLifetime()) {
            discard();
        }
    }

    // 同步数据
    public int getIndex() {
        return entityData.get(INDEX);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public long getStrikeAt() {
        return entityData.get(STRIKE_AT);
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    // 存档（不存档，仅同步）

    @Override
    protected void defineSynchedData() {
        this.entityData.define(INDEX, 0);
        this.entityData.define(LIFETIME, LIFETIME_TICKS);
        this.entityData.define(STRIKE_AT, Long.MAX_VALUE);
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
        buffer.writeInt(targetEntityId);
        buffer.writeInt(entityData.get(INDEX));
        buffer.writeInt(entityData.get(LIFETIME));
        buffer.writeLong(entityData.get(STRIKE_AT));
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        ownerId = buffer.readNullable(FriendlyByteBuf::readUUID);
        targetEntityId = buffer.readInt();
        this.entityData.set(INDEX, buffer.readInt());
        this.entityData.set(LIFETIME, buffer.readInt());
        this.entityData.set(STRIKE_AT, buffer.readLong());
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

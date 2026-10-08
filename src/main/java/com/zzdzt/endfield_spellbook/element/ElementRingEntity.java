package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 元素附着弧环视觉实体：跟随附着目标、贴地显示四段弧（每段 = 1 层）。
 *
 * <p>服务端 tick 跟随目标位置：水平方向三段式（常规速度贴合 → 跳变按比例滑行 →
 * 超过瞬移阈值直接归位），垂直方向锁定跳跃弧线（跑跳不带动弧环）。
 * 目标死亡/移除即消散；寿命 = 附着剩余时间，到点淡出。
 * 纯视觉（{@link SpellVisualOnly}），不存档、无碰撞。
 */
public class ElementRingEntity extends Entity implements IEntityAdditionalSpawnData, SpellVisualOnly {

    private static final EntityDataAccessor<Integer> ELEMENT =
        SynchedEntityData.defineId(ElementRingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STACKS =
        SynchedEntityData.defineId(ElementRingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
        SynchedEntityData.defineId(ElementRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(ElementRingEntity.class, EntityDataSerializers.INT);

    private UUID targetId;

    // ===== 跟随手感 =====

    /** 水平差距超过该值视为瞬移/失控，直接归位（格）。下界须高于击退/激流伴飞的稳态差距（~4），上界低于珍珠级瞬移（10+）。 */
    private static final double SNAP_DISTANCE = 8.0;
    /** 滑行追赶比例系数：比例项保证任何持续速度都收敛（稳态落后 ≈ (目标速度 - 下限) / 该值）。 */
    private static final double CATCH_UP_K = 0.4;
    /** 滑行步长下限（格/tick）：罩住走/跑/疾跑跳的常规速度区间（目标 ≤0.6 格/tick 时逐 tick 贴合）。 */
    private static final double CATCH_UP_MIN = 0.6;
    /** 跳跃弧线最大高度（格）：起跳约 1.25，超过视为真实高度变化（坠落/电梯/飞行），照常跟随。 */
    private static final double JUMP_ARC_MAX_HEIGHT = 1.5;

    /** 仅服务端：目标最近落地点，用于跳跃弧线判定（弧环不存档，无需持久化）。 */
    @Nullable
    private Vec3 groundAnchor;

    public ElementRingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ElementRingEntity(EntityType<?> type, Level level, Vec3 pos, UUID targetId,
                             EndfieldElement element, int stacks, float radius, int lifetime) {
        this(type, level);
        this.setPos(pos);
        this.targetId = targetId;
        this.entityData.set(ELEMENT, element.ordinal());
        this.entityData.set(STACKS, stacks);
        this.entityData.set(RADIUS, radius);
        this.entityData.set(LIFETIME, lifetime);
    }

    @Override
    public void tick() {
        this.baseTick();
        // 尾 10 tick 淡出（GloompurgeMarkEntity 同款机制）
        if (level().isClientSide) return;
        if (tickCount >= getLifetime() - 10 || !followTarget()) {
            discard();
        }
    }

    /**
     * 服务端跟随目标；目标不存在/死亡返回 false。
     *
     * <p>水平三段式：常规速度由 {@code min(dist, ...)} 隐式贴合；击退/瞬移等跳变按
     * "比例追赶 + 下限"滑行（先快后慢，任何持续速度都收敛，无累积状态）；
     * 差距超过 {@link #SNAP_DISTANCE} 直接归位兜底（最坏行为 = 硬跟随，不可能永久掉队）。
     * 垂直方向：目标腾空但高度变化在跳跃幅度内时环停在起跳地面高度，跑跳不带动弧环；
     * 真实升降（坠落/逐格升降/创造飞行）照常跟随。</p>
     */
    private boolean followTarget() {
        if (targetId == null || !(level() instanceof ServerLevel serverLevel)) return true;
        Entity target = serverLevel.getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return false;

        // 跳跃弧线判定：落地点锚点仅在目标落地时刷新
        if (living.onGround()) {
            this.groundAnchor = living.position();
        }
        boolean jumpArc = !living.onGround() && this.groundAnchor != null
            && !(living instanceof Player player && player.getAbilities().flying)
            && Math.abs(living.getY() - this.groundAnchor.y) < JUMP_ARC_MAX_HEIGHT;
        double targetY = jumpArc ? this.groundAnchor.y : living.getY();

        // 水平三段式滑行
        double dx = living.getX() - this.getX();
        double dz = living.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > SNAP_DISTANCE) {
            this.setPos(living.getX(), targetY, living.getZ());
            return true;
        }
        if (dist > 1.0E-4D) {
            double step = Math.min(dist, Math.max(CATCH_UP_MIN, dist * CATCH_UP_K));
            double scale = step / dist;
            this.setPos(this.getX() + dx * scale, targetY, this.getZ() + dz * scale);
        } else {
            this.setPos(this.getX(), targetY, this.getZ());
        }
        return true;
    }

    // 同步数据

    public EndfieldElement getElement() {
        return EndfieldElement.values()[MthClamp(entityData.get(ELEMENT))];
    }

    public int getStacks() {
        return entityData.get(STACKS);
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    @Nullable
    public UUID getTargetId() {
        return targetId;
    }

    private static int MthClamp(int ordinal) {
        EndfieldElement[] values = EndfieldElement.values();
        return Math.max(0, Math.min(values.length - 1, ordinal));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ELEMENT, 0);
        this.entityData.define(STACKS, 1);
        this.entityData.define(RADIUS, 0.9f);
        this.entityData.define(LIFETIME, 160);
    }

    // 存档（不存档，仅同步）

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
        buffer.writeNullable(targetId, FriendlyByteBuf::writeUUID);
        buffer.writeInt(entityData.get(ELEMENT));
        buffer.writeInt(entityData.get(STACKS));
        buffer.writeFloat(entityData.get(RADIUS));
        buffer.writeInt(entityData.get(LIFETIME));
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        targetId = buffer.readNullable(FriendlyByteBuf::readUUID);
        this.entityData.set(ELEMENT, buffer.readInt());
        this.entityData.set(STACKS, buffer.readInt());
        this.entityData.set(RADIUS, buffer.readFloat());
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

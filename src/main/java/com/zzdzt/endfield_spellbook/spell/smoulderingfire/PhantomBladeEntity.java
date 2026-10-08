package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.util.Mth;

/**
 * 幻影魔剑（焚灭 × 主手武器协同，纯视觉）：
 * 投影玩家主手武器，沿"准星斜面"上的弧线横扫一周（与焚灭斩击同向），无伤害。
 *
 * <p>运动模型（服务端权威插值）：弧面由施法视线（含俯仰，ISS FlamingStrike 同款
 * 准星跟随）与水平右向张成——抬头砍，整条弧跟着翘起；角 a 从 startAngle
 * 扫过 sweepAngle（mirrored 反向），位置 = 弧心 + (fwd·cos a + right·sin a) × 半径；
 * 朝向 = 轨迹切向（yRot + xRot）。客户端重写 lerpTo 直接落位（消除原版插值拖影）。
 *
 * <p>SpellVisualOnly，不存档、无碰撞、寿命到即散。
 */
public class PhantomBladeEntity extends Entity implements SpellVisualOnly, IEntityAdditionalSpawnData {

    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_X = new Vec3(1, 0, 0);

    private static final EntityDataAccessor<Integer> DURATION =
        SynchedEntityData.defineId(PhantomBladeEntity.class, EntityDataSerializers.INT);

    /** 弧心悬浮高度（相对锚点）。对齐 ISS FlameStrike 特效平面：脚 + 0.3×身高 + 0.5 ≈ 1.05。 */
    public static final double HEIGHT_OFFSET = 1.05;
    // 播放时长与衔接段占比（SLASH_TICKS / TRANSITION_FRACTION）收拢在 FlameRingCastCurve——魔剑与火环共用时间轴。

    /** 投影的主手武器（仅客户端渲染用，走 spawn data）。 */
    private ItemStack displayStack = ItemStack.EMPTY;
    private Vec3 anchor = Vec3.ZERO;
    private float arcRadius = 3.5f;
    private float startAngle;
    private float sweepAngle = 120f;
    private int duration = FlameRingCastCurve.SLASH_TICKS;
    /** 施法视线俯仰（度，MC 约定正=向下）——弧面跟随准星。 */
    private float lookPitch;
    /** 施法视线水平朝向（MC yaw）。 */
    private float lookYaw;
    /** 背后浮位偏移（相对锚点，衔接段起点）。 */
    private Vec3 chantOffset = Vec3.ZERO;

    public PhantomBladeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param anchor     弧线圆心（施放者脚下位置）
     * @param chantOffset 背后浮位偏移（相对锚点；吟唱漂浮位 → 劈砍起点的衔接起点）
     * @param arcRadius 扫掠半径
     * @param startAngle 起始角（度，0=+Z，与视线水平角同约定，已含镜像）
     * @param sweepAngle 扫过角（度；镜像副手传负值反向）
     * @param lookPitch  施法视线俯仰（度，MC 约定正=向下）
     * @param lookYaw    施法视线水平朝向（MC yaw）
     */
    public PhantomBladeEntity(EntityType<?> type, Level level, ItemStack displayStack,
                              Vec3 anchor, Vec3 chantOffset, double arcRadius,
                              float startAngle, float sweepAngle, boolean mirrored,
                              float lookPitch, float lookYaw) {
        this(type, level);
        this.displayStack = displayStack;
        this.anchor = anchor;
        this.chantOffset = chantOffset;
        this.arcRadius = (float) arcRadius;
        this.startAngle = mirrored ? -startAngle : startAngle;
        this.sweepAngle = mirrored ? -sweepAngle : sweepAngle;
        this.lookPitch = lookPitch;
        this.lookYaw = lookYaw;
        this.duration = FlameRingCastCurve.SLASH_TICKS;
        this.entityData.set(DURATION, duration);
        this.moveTo(anchor.x, anchor.y + HEIGHT_OFFSET, anchor.z, this.startAngle, 0f);
        this.applyArc(0);
    }

    @Override
    public void tick() {
        this.baseTick();
        float t = Mth.clamp(tickCount / (float) duration, 0f, 1f);
        this.applyArc(t);
        if (!level().isClientSide && tickCount >= duration) {
            discard();
        }
    }

    /** 斜面弧线插值：弧面由施法视线（含俯仰）张成，抬头砍弧随准星翘起；朝向 = 轨迹切向（yRot+xRot）。 */
    private void applyArc(float t) {
        Vec3 center = anchor.add(0, HEIGHT_OFFSET, 0);
        Vec3 fwd = Vec3.directionFromRotation(lookPitch, lookYaw);
        Vec3 right = fwd.cross(WORLD_UP);
        if (right.lengthSqr() < 1e-4) {
            right = fwd.cross(WORLD_X); // 视线近竖直时退化保护
        }
        right = right.normalize();
        // 弧起点相对视线方向的偏角（度）：由原水平角约定换算到斜面参数角
        float th0 = startAngle - (float) Math.toDegrees(Math.atan2(fwd.z, fwd.x));

        if (t < FlameRingCastCurve.TRANSITION_FRACTION) {
            // 衔接：浮位 → 弧起点（easeIn 加速飞出）
            double k = t / FlameRingCastCurve.TRANSITION_FRACTION;
            k = k * k;
            Vec3 arcStart = pointOnArc(center, fwd, right, th0);
            Vec3 from = anchor.add(chantOffset);
            this.setPos(
                Mth.lerp(k, from.x, arcStart.x),
                Mth.lerp(k, from.y, arcStart.y),
                Mth.lerp(k, from.z, arcStart.z));
            Vec3 dir = arcStart.subtract(from);
            double len = dir.length();
            this.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            this.setXRot(len < 1e-4 ? 0f
                : (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y / len, -1d, 1d))));
            return;
        }

        float s = (float) ((t - FlameRingCastCurve.TRANSITION_FRACTION) / (1.0 - FlameRingCastCurve.TRANSITION_FRACTION));
        float th = th0 + sweepAngle * s;
        Vec3 pos = pointOnArc(center, fwd, right, th);
        this.setPos(pos.x, pos.y, pos.z);
        // 切向 = (-fwd·sin θ + right·cos θ)（弧面内，含俯仰分量）
        double rad = Math.toRadians(th);
        Vec3 tangent = fwd.scale(-Math.sin(rad)).add(right.scale(Math.cos(rad))).normalize();
        this.setYRot((float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z)));
        this.setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(tangent.y, -1d, 1d))));
    }

    /** 斜面弧线上的点：弧心 + (fwd·cos θ + right·sin θ) × 半径（θ 为相对视线的偏角，度）。 */
    private Vec3 pointOnArc(Vec3 center, Vec3 fwd, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return center.add(fwd.scale(Math.cos(rad) * arcRadius))
            .add(right.scale(Math.sin(rad) * arcRadius));
    }

    // 渲染参数（客户端）

    public ItemStack getDisplayStack() {
        return displayStack;
    }

    public int getDuration() {
        return entityData.get(DURATION);
    }

    public float getSweepAngle() {
        return sweepAngle;
    }

    public float getStartAngle() {
        return startAngle;
    }

    public float getArcRadius() {
        return arcRadius;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DURATION, FlameRingCastCurve.SLASH_TICKS);
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

    /** 客户端直接落位（服务端每 tick 精确插值，消除原版 lerp 拖影）。 */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot,
                       int posRotationIncrements, boolean teleport) {
        this.setPos(x, y, z);
        this.setYRot(yRot);
        this.setXRot(xRot);
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

    // ==================== spawn data ====================

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeItem(displayStack);
        buf.writeDouble(anchor.x);
        buf.writeDouble(anchor.y);
        buf.writeDouble(anchor.z);
        buf.writeDouble(chantOffset.x);
        buf.writeDouble(chantOffset.y);
        buf.writeDouble(chantOffset.z);
        buf.writeFloat(arcRadius);
        buf.writeFloat(startAngle);
        buf.writeFloat(sweepAngle);
        buf.writeInt(duration);
        buf.writeFloat(lookPitch);
        buf.writeFloat(lookYaw);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        displayStack = buf.readItem();
        anchor = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        chantOffset = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        arcRadius = buf.readFloat();
        startAngle = buf.readFloat();
        sweepAngle = buf.readFloat();
        duration = buf.readInt();
        lookPitch = buf.readFloat();
        lookYaw = buf.readFloat();
    }
}

package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.network.NetworkHooks;

/**
 * 焚灭大回环斩（魔剑协同·燃烧回环，纯视觉）：
 * 幻影魔剑砍出火环——环的揭示前段严格跟随剑的扫掠进度（剑尖即环头），
 * 剑砍完 80° 后火焰顺着剑势自传播跑满一圈，满环燃烧片刻后碎裂成切向火星流。
 *
 * <p><b>准星跟随（ISS FlamingStrike 同款）</b>：环面由施法视线（含俯仰）与水平右向
 * 张成——抬头砍，整条环跟着翘起；低头砍，环压向地面。弧上参数角 θ 以视线方向为 0。
 *
 * <p>ISS FlameStrikeParticle 同款方法（无几何、无贴图、无帧动画、零网络撒星）——
 * <b>环不是画出来的，是火焰排布出来的</b>：
 * <ul>
 *   <li><b>环上火苗</b>：原版 FLAME 精子按固定弧距排在环线上（{@link #FLAME_SPACING}），
 *       大小火苗错落、微微上飘——火苗的密度就是环的密度；</li>
 *   <li><b>剑尖亮点</b>：剑扫掠期在剑尖（环头）补火焰与余烬——"这一刀砍出火"的协同瞬间；</li>
 *   <li><b>余烬</b>：沿已揭示弧随机撒 EMBERS（ISS 的贝塞尔撒星，曲线换成整条环弧）；</li>
 *   <li><b>火星</b>：亮头甩 FIERY_SPARKS（切向速度 + Spark 自带重力弧线/落地弹跳），
 *       溃散期整环切向爆发。</li>
 * </ul>
 * 环上不放任何定向平面贴图/几何：环从 360° 全视角被观看，只有 true billboard
 * 粒子全程可读（ISS 的平面斩痕在俯视下会读成"旋转的小火弧"）。
 *
 * <p>固定于生成点（施放者脚下），不跟随移动。
 * SpellVisualOnly，不存档、无碰撞、寿命到即散。
 */
public class FlameRingEntity extends Entity implements SpellVisualOnly {

    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_X = new Vec3(1, 0, 0);

    private static final EntityDataAccessor<Float> RADIUS =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> START_ANGLE =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SWEEP_ANGLE =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOOK_PITCH =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOOK_YAW =
        SynchedEntityData.defineId(FlameRingEntity.class, EntityDataSerializers.FLOAT);

    // 播放总时长与节奏（5 剑砍 + 6 自传播 + 4 满环燃烧 + 5 溃散）收拢在 {@link FlameRingCastCurve}——时间轴唯一真源。
    // 环心高度（相对生成点脚底）：与幻影魔剑弧心同平面——剑砍出环。
    public static final float RING_HEIGHT = (float) PhantomBladeEntity.HEIGHT_OFFSET;

    // ---- 粒子编排节奏（客户端，常量置顶待调） ----
    /** 环上火苗的弧向间距（格）——火苗密度即环的密度。 */
    private static final double FLAME_SPACING = 0.45;
    /** 火苗径向抖动（格），避免排得太"几何"。 */
    private static final double FLAME_RADIAL_JITTER = 0.06;
    /** 小火苗概率分母（1/N 概率换成原版小火苗，大小错落）。 */
    private static final int SMALL_FLAME_CHANCE = 3;
    /** 每 tick 沿已揭示弧随机撒的余烬数。 */
    private static final int EMBER_PER_TICK = 2;
    /** 满环燃烧期每 tick 补充火苗数（四点分布沿圆周推进，避免每帧随机空洞）。 */
    private static final int BURN_FLAME_PER_TICK = 4;
    /** 满环火苗每 tick 沿圆周前进的角度，和实体 ID 无关，保持整体流向稳定。 */
    private static final float BURN_FLAME_PHASE_PER_TICK = 37f;

    public FlameRingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param pos        环心水平基准（施放者脚下位置；环心抬高 {@link #RING_HEIGHT}）
     * @param radius     回环半径（= 魔剑挥砍弧半径，剑尖轨迹贴着环线）
     * @param startAngle 起手角（度，0=+Z 水平角约定，已含镜像）
     * @param sweepAngle 扫过角（度，含符号：幅值=剑砍出的弧段，符号=回环方向）
     * @param lookPitch  施法视线俯仰（度，MC 约定正=向下）——环面跟随准星
     * @param lookYaw    施法视线水平朝向（MC yaw）
     */
    public FlameRingEntity(EntityType<?> type, Level level, Vec3 pos, float radius,
                           float startAngle, float sweepAngle, float lookPitch, float lookYaw) {
        this(type, level);
        this.setPos(pos.x, pos.y, pos.z);
        this.entityData.set(RADIUS, radius);
        this.entityData.set(LIFETIME, FlameRingCastCurve.LIFETIME);
        this.entityData.set(START_ANGLE, startAngle);
        this.entityData.set(SWEEP_ANGLE, sweepAngle);
        this.entityData.set(LOOK_PITCH, lookPitch);
        this.entityData.set(LOOK_YAW, lookYaw);
    }

    @Override
    public void tick() {
        this.baseTick();
        if (level().isClientSide) {
            clientEmbers();
            return;
        }
        if (tickCount >= getLifetime()) {
            discard();
        }
    }

    /**
     * t 时刻已揭示的回环角度（度）——委托 {@link FlameRingCastCurve#revealedDegrees}（时间轴真源）。
     */
    public float revealedDegrees(float t) {
        return FlameRingCastCurve.revealedDegrees(t, Math.abs(getSweepAngle()));
    }

    /**
     * ISS FlameStrikeParticle.createEmberTrail 同款客户端撒星（显式速度向量）：
     * 环上火苗 + 剑尖亮点 + 余烬 + 亮头甩星 → 溃散期整环切向爆发。
     */
    private void clientEmbers() {
        int life = getLifetime();
        if (tickCount >= life - 1) return;

        float dir = Math.signum(getSweepAngle());
        if (dir == 0f) dir = 1f;
        float radius = getRadius();
        float arcPerDeg = radius * (float) (Math.PI / 180);

        float deg = revealedDegrees(tickCount);
        float prevDeg = tickCount > 0 ? revealedDegrees(tickCount - 1) : 0f;
        boolean revealing = deg < 359f;
        boolean burning = !revealing && tickCount < life - FlameRingCastCurve.FADE_TICKS;

        // 准星斜面基向量：弧面由视线（含俯仰）×水平右向张成
        Vec3 center = position().add(0, RING_HEIGHT, 0);
        Vec3 fwd = lookForward();
        Vec3 right = lookRight(fwd);
        float th0 = getStartAngle() - (float) Math.toDegrees(Math.atan2(fwd.z, fwd.x));

        // 环头 = 剑尖（斜面弧线上的当前揭示位置）
        float headTheta = th0 + dir * deg;
        Vec3 head = pointOnArc(center, fwd, right, headTheta, radius);
        // 环头火星必须与剑扫掠方向一致；镜像施法时不能沿反方向甩星。
        Vec3 tangent = arcTangent(fwd, right, headTheta).scale(dir);

        // ① 环上火苗：按固定弧距排布在新揭示的弧段上——火苗排成环，环自然成立
        if (revealing) {
            float arc = deg * arcPerDeg;
            float prevArc = prevDeg * arcPerDeg;
            int k0 = (int) Math.floor(prevArc / FLAME_SPACING) + 1;
            int k1 = (int) Math.floor(arc / FLAME_SPACING);
            for (int k = k0; k <= k1; k++) {
                float theta = th0 + dir * (float) (k * FLAME_SPACING / arcPerDeg);
                spawnRingFlame(center, fwd, right, theta, radius, dir);
            }
        } else if (burning) {
            // P4.4：满环燃烧时以四个等距采样点补火，整体随时间沿环向推进。
            // 位置规律稳定，只有单个火苗的径向抖动与大小仍保留轻微随机性。
            float burnStart = life - FlameRingCastCurve.FADE_TICKS - FlameRingCastCurve.BURN_TICKS;
            float burnElapsed = tickCount - burnStart;
            float phase = burnElapsed * BURN_FLAME_PHASE_PER_TICK;
            for (int i = 0; i < BURN_FLAME_PER_TICK; i++) {
                float theta = th0 + dir * (360f * (i + 0.5f) / BURN_FLAME_PER_TICK + phase);
                spawnRingFlame(center, fwd, right, theta, radius, dir);
            }
        }

        // ② 剑尖亮点（魔剑协同的"砍出"瞬间）：剑扫掠期在剑尖补火焰与余烬
        if (revealing && tickCount <= FlameRingCastCurve.SLASH_TICKS) {
            level().addParticle(ParticleTypes.FLAME, head.x, head.y, head.z,
                tangent.x * 0.02, 0.02, tangent.z * 0.02);
            level().addParticle(ParticleHelper.EMBERS, head.x, head.y, head.z,
                tangent.x * 0.1, 0.05, tangent.z * 0.1);
        }

        // P4.2：闭环后将最初的收尾点保留为视觉焦点，满环燃烧期间每隔一 tick 补充短促的火焰与余烬。
        // 不改变几何揭示进度，也不额外延长 FlameRingCastCurve 的 4 tick 满环燃烧时长。
        if (burning && (tickCount & 1) == 0) {
            level().addParticle(ParticleTypes.FLAME, head.x, head.y, head.z,
                tangent.x * 0.045, 0.025, tangent.z * 0.045);
            level().addParticle(ParticleHelper.EMBERS, head.x, head.y, head.z,
                tangent.x * 0.13, 0.055, tangent.z * 0.13);
        }

        // ③ 余烬：沿已揭示弧随机撒（ISS 的贝塞尔撒星，曲线换成整条环弧）
        if (revealing || burning) {
            for (int i = 0; i < EMBER_PER_TICK; i++) {
                float theta = th0 + dir * deg * random.nextFloat();
                Vec3 p = pointOnArc(center, fwd, right, theta, radius);
                level().addParticle(ParticleHelper.EMBERS, p.x, p.y, p.z, 0, 0.04, 0);
            }
        }

        // ④ 亮头甩出的火星（Spark 重力弧线 + 落地弹跳）
        if (revealing) {
            float speed = 0.8f + random.nextFloat() * 0.8f;
            level().addParticle(ParticleHelper.FIERY_SPARKS,
                head.x, head.y, head.z,
                tangent.x * speed, 0.12 + random.nextFloat() * 0.15, tangent.z * speed);
        }

        // ⑤ P4.1 溃散期：使用与几何 Renderer 相同的 32 个稳定片段槽。
        // 火星从各片段的真实脱离位置出发，方向、位移与拉伸曲线都由 FlameRingBreakup 共享。
        if (tickCount >= life - FlameRingCastCurve.FADE_TICKS) {
            float fadeStart = life - FlameRingCastCurve.FADE_TICKS;
            float d = Mth.clamp((tickCount - fadeStart) / (float) FlameRingCastCurve.FADE_TICKS, 0f, 1f);
            float previousD = Mth.clamp((tickCount - 1f - fadeStart)
                / (float) FlameRingCastCurve.FADE_TICKS, 0f, 1f);
            Vec3 planeNormal = fwd.cross(right).normalize();

            // 参考帧 10–12：环体开始裂解时，收尾剑锋继续甩出少量切向火星；
            // 到 13–16 的衰减段逐步停止，避免头部亮点在其他环段消失后突兀残留。
            float headFade = 1f - FlameRingBreakup.smoothstep(0.35f, 0.88f, d);
            if (headFade > 0.08f) {
                level().addParticle(ParticleHelper.FIERY_SPARKS,
                    head.x, head.y, head.z,
                    tangent.x * (0.42 + 0.38 * headFade),
                    tangent.y * (0.42 + 0.38 * headFade) + 0.10 + planeNormal.y * 0.05,
                    tangent.z * (0.42 + 0.38 * headFade));
                if ((tickCount & 1) == 0 && headFade > 0.30f) {
                    level().addParticle(ParticleHelper.EMBERS, head.x, head.y, head.z,
                        tangent.x * 0.16, tangent.y * 0.16 + 0.04, tangent.z * 0.16);
                }
            }

            for (int fragment = 0; fragment < FlameRingBreakup.FRAGMENT_COUNT; fragment++) {
                float progress = FlameRingBreakup.progress(d, fragment, getId());
                float previousProgress = FlameRingBreakup.progress(previousD, fragment, getId());
                if (progress <= 0f) continue;

                float theta = th0 + dir * 360f * FlameRingBreakup.fragmentCenter(fragment);
                Vec3 original = pointOnArc(center, fwd, right, theta, radius);
                Vec3 radial = radial(fwd, right, theta);
                Vec3 fragmentTangent = arcTangent(fwd, right, theta).scale(dir);
                Vec3 released = original
                    .add(fragmentTangent.scale(FlameRingBreakup.tangentialDistance(progress, fragment, getId())))
                    .add(radial.scale(FlameRingBreakup.radialDistance(progress, fragment, getId())))
                    .add(planeNormal.scale(FlameRingBreakup.normalDistance(progress, fragment, getId())));

                // 每个片段在开始脱离时只触发一次主火星，杜绝每 tick 重复整圈随机爆发。
                if (progress >= 0.02f && previousProgress < 0.02f) {
                    float speed = 0.95f + FlameRingBreakup.hash01(fragment, 5, getId()) * 1.10f;
                    float radialKick = (FlameRingBreakup.hash01(fragment, 6, getId()) - 0.5f) * 0.42f;
                    level().addParticle(ParticleHelper.FIERY_SPARKS,
                        released.x, released.y, released.z,
                        fragmentTangent.x * speed + radial.x * radialKick,
                        fragmentTangent.y * speed + 0.12 + planeNormal.y * 0.08,
                        fragmentTangent.z * speed + radial.z * radialKick);
                }

                // 中段持续少量喷出同方向火星，形成片段拉长后的火流；尾段转为稀疏余烬。
                if (progress > 0.18f && progress < 0.86f
                    && FlameRingBreakup.hash01(fragment, 7, getId()) < 0.40f) {
                    float speed = 0.65f + 0.85f * FlameRingBreakup.travel(progress);
                    level().addParticle(ParticleHelper.FIERY_SPARKS,
                        released.x, released.y, released.z,
                        fragmentTangent.x * speed, fragmentTangent.y * speed + 0.10 + planeNormal.y * 0.05,
                        fragmentTangent.z * speed);
                }
                if (progress > 0.55f && progress < 0.94f
                    && FlameRingBreakup.hash01(fragment, 8, getId()) < 0.18f) {
                    level().addParticle(ParticleHelper.EMBERS,
                        released.x, released.y, released.z,
                        fragmentTangent.x * 0.22, fragmentTangent.y * 0.22 + 0.035,
                        fragmentTangent.z * 0.22);
                }
            }
        }
    }

    /** 环上火苗：原版火焰精灵贴环线生成，大小错落、微切向漂移 + 缓慢上飘。 */
    private void spawnRingFlame(Vec3 center, Vec3 fwd, Vec3 right, float thetaDeg,
                                float radius, float dir) {
        float r = radius + (float) ((random.nextDouble() - 0.5) * 2 * FLAME_RADIAL_JITTER);
        Vec3 p = pointOnArc(center, fwd, right, thetaDeg, r);
        Vec3 tangent = arcTangent(fwd, right, thetaDeg);
        var type = random.nextInt(SMALL_FLAME_CHANCE) == 0
            ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
        level().addParticle(type, p.x, p.y, p.z,
            tangent.x * 0.02, 0.01 + random.nextDouble() * 0.02, tangent.z * 0.02);
    }

    /** 施法视线向量（含俯仰，ISS 同款准星跟随）。 */
    private Vec3 lookForward() {
        return Vec3.directionFromRotation(getLookPitch(), getLookYaw());
    }

    /** 水平右向：视线 × 世界Up（视线近竖直时退化保护）。 */
    private static Vec3 lookRight(Vec3 fwd) {
        Vec3 right = fwd.cross(WORLD_UP);
        if (right.lengthSqr() < 1e-4) {
            right = fwd.cross(WORLD_X);
        }
        return right.normalize();
    }

    /** 斜面环线上 θ 偏角处的点：环心 + (fwd·cos θ + right·sin θ) × 半径。 */
    private static Vec3 pointOnArc(Vec3 center, Vec3 fwd, Vec3 right, float thetaDeg, float radius) {
        double rad = Math.toRadians(thetaDeg);
        return center.add(fwd.scale(Math.cos(rad) * radius))
            .add(right.scale(Math.sin(rad) * radius));
    }

    /** 斜面环线在 θ 处的切向（弧面内，含俯仰分量）。 */
    private static Vec3 arcTangent(Vec3 fwd, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return fwd.scale(-Math.sin(rad)).add(right.scale(Math.cos(rad))).normalize();
    }

    /** 斜面环线在 θ 处的径向（弧面内，指向环外；与 FlameRingRenderer.radial 同式）。 */
    private static Vec3 radial(Vec3 fwd, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return fwd.scale(Math.cos(rad)).add(right.scale(Math.sin(rad))).normalize();
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public float getStartAngle() {
        return entityData.get(START_ANGLE);
    }

    public float getSweepAngle() {
        return entityData.get(SWEEP_ANGLE);
    }

    public float getLookPitch() {
        return entityData.get(LOOK_PITCH);
    }

    public float getLookYaw() {
        return entityData.get(LOOK_YAW);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RADIUS, 3.5f);
        this.entityData.define(LIFETIME, FlameRingCastCurve.LIFETIME);
        this.entityData.define(START_ANGLE, 0f);
        this.entityData.define(SWEEP_ANGLE, 360f);
        this.entityData.define(LOOK_PITCH, 0f);
        this.entityData.define(LOOK_YAW, 0f);
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

package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.client.post.FlameRingSnapshot;
import com.zzdzt.endfield_spellbook.client.post.PipelinePost;
import com.zzdzt.endfield_spellbook.client.post.PostRenderTypes;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 焚灭大回环斩渲染器（V2 P3 3D Volume + Flow）。
 *
 * <p>环的主体由三层可挤出体积 Ribbon 承担（外焰光晕 / 主焰轮廓 / 炽核），
 * 粒子编排（{@link FlameRingEntity} clientEmbers）继续负责火焰细节：
 * <ul>
 *     <li>揭示阶段：只绘制 startAngle -> revealedDegrees() 的弧段，环头=剑尖（headBoost 增亮）；</li>
 *     <li>自传播阶段：弧段继续增长到 360°；</li>
 *     <li>满环阶段：保持连续火焰带，并沿环向产生多频流动/鼓包；</li>
 *     <li>溃散阶段：按稳定随机阈值切断体积带，残片沿切向 + 径向 + 法向做三维甩散。</li>
 * </ul>
 *
 * <p>时序唯一真源是 {@link FlameRingEntity#revealedDegrees(float)}，
 * 渲染器不维护第二套状态机。环面基向量由 lookPitch/lookYaw 张成——跟随施法准星含俯仰。
 *
 * <p>资源：{@code assets/endfield_spellbook/textures/vfx/fire_ring.png}（512x128，横向可平铺）。
 */
public class FlameRingRenderer extends EntityRenderer<FlameRingEntity> {

    private static final ResourceLocation FIRE_RING = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "textures/vfx/fire_ring.png");

    /** 后处理关闭时的 fallback：加算 + 粒子目标，与其它自研能量特效保持一致。 */
    private static final RenderType VANILLA_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "flame_ring_v1_fallback", FIRE_RING, false);

    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_X = new Vec3(1, 0, 0);
    private static final Vec3 DEFAULT_VERTEX_NORMAL = new Vec3(0, 0, 1);

    // ---------- 几何（三层带宽已放大 50%：0.34/0.22/0.10 → 0.51/0.33/0.15） ----------
    private static final float SEGMENT_LENGTH = 0.20f;
    private static final float OUTER_WIDTH = 0.51f;
    private static final float BODY_WIDTH = 0.33f;
    private static final float CORE_WIDTH = 0.15f;
    private static final float BREAK_DISTANCE = 0.75f;

    // ---------- P1 火焰形体 ----------
    private static final float TONGUE_CHANCE = 0.24f;
    private static final float TONGUE_MIN_LENGTH = 0.16f;
    private static final float TONGUE_MAX_LENGTH = 0.52f;
    private static final float TONGUE_MIN_WIDTH = 0.045f;
    private static final float TONGUE_MAX_WIDTH = 0.12f;
    private static final float HEAD_FORWARD = 0.62f;
    private static final float HEAD_SIDE = 0.22f;

    // ---------- P3 三维体积 / 流动 ----------
    private static final float OUTER_DEPTH = 0.16f;
    private static final float BODY_DEPTH = 0.11f;
    private static final float CORE_DEPTH = 0.055f;
    private static final float FLOW_BULGE = 0.11f;
    private static final float FLOW_SPEED = 1.05f;

    // ---------- 纹理/动画 ----------
    private static final float U_TILING = 2.8f;
    private static final float UV_SCROLL_PER_TICK = 0.11f;
    private static final float NOISE_SPEED = 0.33f;

    // ---------- 时间 ----------
    private static final float HEAD_BOOST_LENGTH = 0.90f;
    private static final float FULL_RING_BURN_ALPHA = 0.96f;

    public FlameRingRenderer(Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(FlameRingEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTicks;

        // Post pipeline：实体 pass 只冻结参数；AFTER_LEVEL 才真正画入自研 FBO。
        if (PipelinePost.isActive()) {
            PipelinePost.enqueue(new FlameRingSnapshot(entity, age));
            super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
            return;
        }

        poseStack.pushPose();
        drawRing(entity, age, poseStack, bufferSource, VANILLA_TYPE);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    /**
     * 后处理管线重绘入口：poseStack 已由 PipelinePost 平移到实体位置（相机为原点）。
     */
    public void drawForPipeline(FlameRingEntity entity, float age,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                PostRenderTypes.FlameRingRenderTypeSet set) {
        drawRing(entity, age, poseStack, bufferSource, set.outer(), set.body(), set.core());
    }

    /** 双路径共用的火环主体绘制。 */
    private void drawRing(FlameRingEntity entity, float age,
                          PoseStack poseStack, MultiBufferSource bufferSource,
                          RenderType type) {
        drawRing(entity, age, poseStack, bufferSource, type, type, type);
    }

    private void drawRing(FlameRingEntity entity, float age,
                          PoseStack poseStack, MultiBufferSource bufferSource,
                          RenderType outerType, RenderType bodyType, RenderType coreType) {
        int life = Math.max(1, entity.getLifetime());
        float fadeStart = life - FlameRingCastCurve.FADE_TICKS;

        float revealDeg = Mth.clamp(entity.revealedDegrees(age), 0f, 360f);
        if (revealDeg <= 0.001f || age >= life) {
            return;
        }

        // 溃散进度：0 = 正常；1 = 完全解体。
        float breakup = Mth.clamp((age - fadeStart) / (float) FlameRingCastCurve.FADE_TICKS, 0f, 1f);
        float burnAlpha = age >= fadeStart ? 1f - breakup : FULL_RING_BURN_ALPHA;

        Vec3 center = new Vec3(0, FlameRingEntity.RING_HEIGHT, 0);
        Vec3 fwd = lookForward(entity);
        Vec3 right = lookRight(fwd);
        Vec3 planeNormal = fwd.cross(right).normalize();
        float theta0 = entity.getStartAngle()
            - (float) Math.toDegrees(Math.atan2(fwd.z, fwd.x));
        float dir = Math.signum(entity.getSweepAngle());
        if (dir == 0f) dir = 1f;

        float radius = entity.getRadius();
        float arcLength = (float) (Math.toRadians(revealDeg) * radius);
        int segments = Mth.clamp((int) Math.ceil(arcLength / SEGMENT_LENGTH), 8, 220);

        VertexConsumer outer = bufferSource.getBuffer(outerType);
        VertexConsumer body = bufferSource.getBuffer(bodyType);
        VertexConsumer core = bufferSource.getBuffer(coreType);

        poseStack.pushPose();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // 三层染色：外焰 / 主焰 / 炽核。
        renderLayer(entity, pose, normal, outer, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, OUTER_WIDTH, 0.68f, 0.27f, 0.21f, burnAlpha, 0, planeNormal);
        renderLayer(entity, pose, normal, body, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, BODY_WIDTH, 0.92f, 0.41f, 0.23f, burnAlpha * 0.95f, 1, planeNormal);
        renderLayer(entity, pose, normal, core, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, CORE_WIDTH, 1.0f, 0.84f, 0.56f, burnAlpha * 0.72f, 2, planeNormal);

        renderFlameTongues(entity, pose, normal, outer, center, fwd, right,
            theta0, dir, revealDeg, radius, segments, age, breakup, burnAlpha * 0.92f, planeNormal);
        renderHead(entity, pose, normal, outer, center, fwd, right,
            theta0, dir, revealDeg, radius, age, burnAlpha, planeNormal);
        renderBreakupFragments(entity, pose, normal, body, center, fwd, right,
            theta0, dir, radius, segments, breakup, burnAlpha, planeNormal);

        poseStack.popPose();
    }

    private void renderLayer(
        FlameRingEntity entity,
        Matrix4f pose,
        Matrix3f normal,
        VertexConsumer consumer,
        Vec3 center,
        Vec3 fwd,
        Vec3 right,
        float theta0,
        float dir,
        float revealDeg,
        float radius,
        int segments,
        float age,
        float breakup,
        float width,
        float r,
        float g,
        float b,
        float alpha,
        int layer,
        Vec3 planeNormal
    ) {
        float revealedArc = (float) Math.toRadians(revealDeg) * radius;
        float scroll = age * UV_SCROLL_PER_TICK * (1f + layer * 0.12f);
        float depthScale = volumeDepth(layer);

        for (int i = 0; i < segments; i++) {
            float s0 = i / (float) segments;
            float s1 = (i + 1) / (float) segments;
            float a0 = revealDeg * s0;
            float a1 = revealDeg * s1;

            if (breakup > 0f && breakupThreshold(i, layer, entity.getId()) < breakup) {
                continue;
            }

            float mid = (s0 + s1) * 0.5f;
            float headDistance = (1f - mid) * revealedArc;
            float headBoost = revealDeg < 359f
                ? (float) Math.exp(-Math.pow(headDistance / HEAD_BOOST_LENGTH, 2.0))
                : 0f;

            float wobble0 = flameWobble(i, age, layer);
            float wobble1 = flameWobble(i + 1, age, layer);
            float flow0 = flowProfile(s0, age, layer, entity.getId());
            float flow1 = flowProfile(s1, age, layer, entity.getId());

            float widthMul = 0.76f
                + 0.34f * ((wobble0 + wobble1) * 0.5f)
                + 0.85f * headBoost
                + 0.12f * ((flow0 + flow1) * 0.5f);
            if (breakup > 0f) {
                widthMul *= 1f - breakup * 0.30f;
            }
            float halfWidth = width * Math.max(0.42f, widthMul) * 0.5f;

            Vec3 p0 = pointOnArc(center, fwd, right, theta0 + dir * a0, radius);
            Vec3 p1 = pointOnArc(center, fwd, right, theta0 + dir * a1, radius);
            Vec3 radial0 = radial(fwd, right, theta0 + dir * a0);
            Vec3 radial1 = radial(fwd, right, theta0 + dir * a1);

            float wobbleMid = (wobble0 + wobble1) * 0.5f;
            float outerMul = 1.0f
                + 0.26f * wobbleMid
                + 0.22f * headBoost
                + 0.10f * ((flow0 + flow1) * 0.5f);
            float innerMul = 0.72f + 0.12f * (1f - wobbleMid);

            // P3：把平面 Ribbon 挤出为真正有前/后表面 + 内/外侧壁的体积带。
            // flow 在环向移动：鼓包沿 segment 参数传播，而不是随机静态噪声。
            float bulge0 = depthScale * FLOW_BULGE
                * (0.22f + 0.78f * flow0) * (0.78f + 0.22f * wobble0);
            float bulge1 = depthScale * FLOW_BULGE
                * (0.22f + 0.78f * flow1) * (0.78f + 0.22f * wobble1);

            float thickness = depthScale
                * (0.82f + 0.28f * wobbleMid + 0.42f * headBoost);

            float frontOuter0 = thickness * 0.54f + bulge0;
            float frontInner0 = thickness * 0.31f + bulge0 * 0.55f;
            float backOuter0 = -thickness * 0.46f + bulge0 * 0.42f;
            float backInner0 = -thickness * 0.29f + bulge0 * 0.22f;

            float frontOuter1 = thickness * 0.54f + bulge1;
            float frontInner1 = thickness * 0.31f + bulge1 * 0.55f;
            float backOuter1 = -thickness * 0.46f + bulge1 * 0.42f;
            float backInner1 = -thickness * 0.29f + bulge1 * 0.22f;

            Vec3 q0OuterFront = p0.add(radial0.scale(halfWidth * outerMul))
                .add(planeNormal.scale(frontOuter0));
            Vec3 q0InnerFront = p0.subtract(radial0.scale(halfWidth * innerMul))
                .add(planeNormal.scale(frontInner0));
            Vec3 q0OuterBack = p0.add(radial0.scale(halfWidth * outerMul))
                .add(planeNormal.scale(backOuter0));
            Vec3 q0InnerBack = p0.subtract(radial0.scale(halfWidth * innerMul))
                .add(planeNormal.scale(backInner0));

            Vec3 q1OuterFront = p1.add(radial1.scale(halfWidth * outerMul))
                .add(planeNormal.scale(frontOuter1));
            Vec3 q1InnerFront = p1.subtract(radial1.scale(halfWidth * innerMul))
                .add(planeNormal.scale(frontInner1));
            Vec3 q1OuterBack = p1.add(radial1.scale(halfWidth * outerMul))
                .add(planeNormal.scale(backOuter1));
            Vec3 q1InnerBack = p1.subtract(radial1.scale(halfWidth * innerMul))
                .add(planeNormal.scale(backInner1));

            float phase0 = s0 * 31f - age * FLOW_SPEED * (1f + layer * 0.12f);
            float phase1 = s1 * 31f - age * FLOW_SPEED * (1f + layer * 0.12f);
            float u0 = fract(s0 * U_TILING + scroll + (float) Math.sin(phase0) * 0.035f);
            float u1 = fract(s1 * U_TILING + scroll + (float) Math.sin(phase1) * 0.035f);

            float a = Mth.clamp(alpha * (1f + headBoost * 1.25f), 0f, 1f);
            if (breakup > 0f) {
                a *= 0.35f + 0.65f * (1f - breakup);
            }

            // 前表面：主要视觉体积，保留完整三材质纹理。
            putQuad(consumer, pose, normal,
                q0OuterFront, q0InnerFront, q1InnerFront, q1OuterFront,
                planeNormal, u0, u1, r, g, b, a * 0.88f, 0f, 1f);

            // 后表面：较暗、较薄，避免火环从斜角观看时完全变成一张纸。
            putQuad(consumer, pose, normal,
                q0InnerBack, q0OuterBack, q1OuterBack, q1InnerBack,
                planeNormal.scale(-1.0), u0, u1, r, g, b, a * 0.38f, 1f, 0f);

            // 外侧壁：承接前/后表面，负责视角变化时的“火焰厚边”。
            putQuad(consumer, pose, normal,
                q0OuterFront, q0OuterBack, q1OuterBack, q1OuterFront,
                radial0.add(radial1).normalize(), u0, u1, r, g, b, a * 0.58f, 0f, 1f);

            // 内侧壁：更暗，避免内圈变成厚重实心橡胶圈。
            putQuad(consumer, pose, normal,
                q0InnerBack, q0InnerFront, q1InnerFront, q1InnerBack,
                radial0.add(radial1).scale(-1.0).normalize(), u0, u1, r, g, b, a * 0.30f, 1f, 0f);
        }
    }

    /** P1：外缘离散火舌。 */
    private void renderFlameTongues(FlameRingEntity entity, Matrix4f pose, Matrix3f normal,
                                    VertexConsumer consumer, Vec3 center, Vec3 fwd, Vec3 right,
                                    float theta0, float dir, float revealDeg, float radius,
                                    int segments, float age, float breakup, float alpha, Vec3 planeNormal) {
        if (revealDeg <= 1f) return;

        for (int i = 0; i < segments; i++) {
            if (breakupHash(i, 7, entity.getId()) > TONGUE_CHANCE) continue;

            float s = (i + 0.37f) / segments;
            float theta = theta0 + dir * revealDeg * s;
            float local = breakupHash(i, 8, entity.getId());
            float pulse = flameWobble(i + 31, age * 1.12f, 4);
            float length = Mth.lerp(local, TONGUE_MIN_LENGTH, TONGUE_MAX_LENGTH)
                * (0.72f + 0.48f * pulse);
            float half = Mth.lerp(breakupHash(i, 9, entity.getId()),
                TONGUE_MIN_WIDTH, TONGUE_MAX_WIDTH) * 0.5f;

            Vec3 p = pointOnArc(center, fwd, right, theta, radius + OUTER_WIDTH * 0.28f);
            Vec3 rad = radial(fwd, right, theta);
            Vec3 tan = arcTangent(fwd, right, theta);
            float lean = (breakupHash(i, 10, entity.getId()) - 0.5f) * 0.34f;
            Vec3 tip = p.add(rad.scale(length))
                .add(tan.scale(length * lean))
                .add(planeNormal.scale((pulse - 0.5f) * 0.075f));
            Vec3 side = tan.scale(half);
            float tongueDepth = 0.010f + 0.020f * pulse;
            Vec3 baseA = p.subtract(side).add(planeNormal.scale(tongueDepth));
            Vec3 baseB = p.add(side).add(planeNormal.scale(tongueDepth));
            Vec3 tipA = tip.add(side.scale(0.18f)).add(planeNormal.scale(tongueDepth));
            Vec3 tipB = tip.subtract(side.scale(0.18f)).add(planeNormal.scale(tongueDepth));

            float a = alpha * (0.34f + 0.42f * pulse);
            if (breakup > 0f) {
                a *= 0.35f + 0.65f * (1f - breakup);
            }

            putVertex(consumer, pose, normal, baseA, 0f, 1f, 0.98f, 0.38f, 0.16f, a);
            putVertex(consumer, pose, normal, baseB, 1f, 1f, 0.98f, 0.38f, 0.16f, a);
            putVertex(consumer, pose, normal, tipB, 1f, 0f, 1f, 0.82f, 0.40f, a * 0.10f);
            putVertex(consumer, pose, normal, tipA, 0f, 0f, 1f, 0.82f, 0.40f, a * 0.10f);
        }
    }

    /** P1：独立攻击性环头。 */
    private void renderHead(FlameRingEntity entity, Matrix4f pose, Matrix3f normal,
                            VertexConsumer consumer, Vec3 center, Vec3 fwd, Vec3 right,
                            float theta0, float dir, float revealDeg, float radius,
                            float age, float alpha, Vec3 planeNormal) {
        if (revealDeg >= 359f) return;

        float theta = theta0 + dir * revealDeg;
        Vec3 head = pointOnArc(center, fwd, right, theta, radius);
        Vec3 tangent = arcTangent(fwd, right, theta).scale(dir);
        Vec3 rad = radial(fwd, right, theta);
        Vec3 side = rad.scale(OUTER_WIDTH * 0.42f);

        float headDepth = 0.032f + 0.028f * (0.5f + 0.5f * Mth.sin(age * 1.7f));
        Vec3 baseL = head.subtract(tangent.scale(0.12f)).subtract(side).add(planeNormal.scale(headDepth * 0.72f));
        Vec3 baseR = head.subtract(tangent.scale(0.12f)).add(side).add(planeNormal.scale(headDepth * 0.72f));
        Vec3 tip = head.add(tangent.scale(HEAD_FORWARD)).add(rad.scale(0.12f))
            .add(planeNormal.scale(headDepth));
        float a = alpha * (0.80f + 0.20f * (0.5f + 0.5f * Mth.sin(age * 0.9f)));

        putVertex(consumer, pose, normal, baseL, 0f, 1f, 1f, 0.49f, 0.19f, a);
        putVertex(consumer, pose, normal, baseR, 1f, 1f, 1f, 0.49f, 0.19f, a);
        putVertex(consumer, pose, normal, tip, 0.5f, 0f, 1f, 0.92f, 0.58f, a * 0.08f);
        putVertex(consumer, pose, normal, tip, 0.5f, 0f, 1f, 0.92f, 0.58f, a * 0.08f);

        for (int k = -1; k <= 1; k += 2) {
            Vec3 base = head.add(rad.scale(k * HEAD_SIDE * 0.45f)).subtract(tangent.scale(0.05f));
            Vec3 tipSide = base.add(tangent.scale(0.28f + 0.08f * k)).add(rad.scale(k * HEAD_SIDE));
            Vec3 s = rad.scale(0.07f);
            putVertex(consumer, pose, normal, base.subtract(s), 0f, 1f, 0.96f, 0.40f, 0.14f, a * 0.62f);
            putVertex(consumer, pose, normal, base.add(s), 1f, 1f, 0.96f, 0.40f, 0.14f, a * 0.62f);
            putVertex(consumer, pose, normal, tipSide, 0.5f, 0f, 1f, 0.72f, 0.35f, a * 0.05f);
            putVertex(consumer, pose, normal, tipSide, 0.5f, 0f, 1f, 0.72f, 0.35f, a * 0.05f);
        }
    }

    /** P1：溃散中间态残片。 */
    private void renderBreakupFragments(FlameRingEntity entity, Matrix4f pose, Matrix3f normal,
                                        VertexConsumer consumer, Vec3 center, Vec3 fwd, Vec3 right,
                                        float theta0, float dir, float radius, int segments,
                                        float breakup, float alpha, Vec3 planeNormal) {
        if (breakup <= 0f) return;

        float stage = Mth.clamp(breakup * 1.45f, 0f, 1f);
        float motion = stage * stage;
        for (int i = 0; i < segments; i++) {
            float h = breakupHash(i, 11, entity.getId());
            if (h > breakup * 1.22f) continue;

            float s = (i + 0.5f) / segments;
            float theta = theta0 + dir * 360f * s;
            Vec3 p = pointOnArc(center, fwd, right, theta, radius);
            Vec3 rad = radial(fwd, right, theta);
            Vec3 tan = arcTangent(fwd, right, theta).scale(dir);

            float length = 0.14f + 0.36f * h;
            float width = 0.05f + 0.07f * (1f - h);
            Vec3 offset = tan.scale(BREAK_DISTANCE * motion * (0.35f + 0.95f * h))
                .add(rad.scale(BREAK_DISTANCE * motion * (h - 0.45f) * 0.55f))
                .add(planeNormal.scale(BREAK_DISTANCE * motion * (h - 0.5f) * 0.42f));
            Vec3 centerP = p.add(offset).add(planeNormal.scale(0.01f));
            Vec3 baseL = centerP.subtract(tan.scale(length * 0.5f)).subtract(rad.scale(width));
            Vec3 baseR = centerP.subtract(tan.scale(length * 0.5f)).add(rad.scale(width));
            Vec3 tip = centerP.add(tan.scale(length * 0.8f))
                .add(rad.scale(width * (0.8f + 1.4f * h)));
            float a = alpha * (1f - stage) * (0.35f + 0.55f * h);

            putVertex(consumer, pose, normal, baseL, 0f, 1f, 0.94f, 0.35f, 0.12f, a);
            putVertex(consumer, pose, normal, baseR, 1f, 1f, 1.0f, 0.52f, 0.18f, a);
            putVertex(consumer, pose, normal, tip, 0.5f, 0f, 1f, 0.82f, 0.40f, a * 0.05f);
            putVertex(consumer, pose, normal, tip, 0.5f, 0f, 1f, 0.82f, 0.40f, a * 0.05f);
        }
    }

    private static void putVertex(
        VertexConsumer consumer,
        Matrix4f pose,
        Matrix3f normal,
        Vec3 pos,
        float u,
        float v,
        float r,
        float g,
        float b,
        float a
    ) {
        putVertex(consumer, pose, normal, pos, u, v, r, g, b, a,
            DEFAULT_VERTEX_NORMAL);
    }

    private static void putVertex(
        VertexConsumer consumer,
        Matrix4f pose,
        Matrix3f normal,
        Vec3 pos,
        float u,
        float v,
        float r,
        float g,
        float b,
        float a,
        Vec3 vertexNormal
    ) {
        consumer.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normal, (float) vertexNormal.x, (float) vertexNormal.y, (float) vertexNormal.z)
            .endVertex();
    }

    private static float volumeDepth(int layer) {
        return switch (layer) {
            case 0 -> OUTER_DEPTH;
            case 1 -> BODY_DEPTH;
            default -> CORE_DEPTH;
        };
    }

    /**
     * 环向流场：低频波负责大鼓包，高频波负责火焰局部翻卷。
     * s=0..1 始终沿当前可见弧段从尾端走向环头，时间只改变相位，
     * 因此不会破坏 Reveal Curve 的唯一时序。
     */
    private static float flowProfile(float s, float age, int layer, int entityId) {
        float phase = s * 32f
            - age * FLOW_SPEED * (1f + layer * 0.12f)
            + entityId * 0.017f
            + layer * 1.37f;
        float low = 0.5f + 0.5f * Mth.sin(phase);
        float mid = 0.5f + 0.5f * Mth.sin(phase * 2.17f + 1.4f);
        float high = 0.5f + 0.5f * Mth.sin(phase * 5.31f - 0.8f);
        return Mth.clamp(0.14f + 0.54f * low + 0.24f * mid + 0.08f * high, 0f, 1f);
    }

    private static void putQuad(
        VertexConsumer consumer,
        Matrix4f pose,
        Matrix3f normal,
        Vec3 q0,
        Vec3 q1,
        Vec3 q2,
        Vec3 q3,
        Vec3 faceNormal,
        float u0,
        float u1,
        float r,
        float g,
        float b,
        float alpha,
        float v0,
        float v1
    ) {
        putVertex(consumer, pose, normal, q0, u0, v0, r, g, b, alpha, faceNormal);
        putVertex(consumer, pose, normal, q1, u0, v1, r, g, b, alpha * 0.82f, faceNormal);
        putVertex(consumer, pose, normal, q2, u1, v1, r, g, b, alpha * 0.82f, faceNormal);
        putVertex(consumer, pose, normal, q3, u1, v0, r, g, b, alpha, faceNormal);
    }

    private static Vec3 pointOnArc(Vec3 center, Vec3 fwd, Vec3 right, float thetaDeg, float radius) {
        double rad = Math.toRadians(thetaDeg);
        return center
            .add(fwd.scale(Math.cos(rad) * radius))
            .add(right.scale(Math.sin(rad) * radius));
    }

    private static Vec3 radial(Vec3 fwd, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return fwd.scale(Math.cos(rad)).add(right.scale(Math.sin(rad))).normalize();
    }

    private static Vec3 arcTangent(Vec3 fwd, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return fwd.scale(-Math.sin(rad)).add(right.scale(Math.cos(rad))).normalize();
    }

    private static Vec3 lookForward(FlameRingEntity entity) {
        return Vec3.directionFromRotation(entity.getLookPitch(), entity.getLookYaw()).normalize();
    }

    private static Vec3 lookRight(Vec3 fwd) {
        Vec3 right = fwd.cross(WORLD_UP);
        if (right.lengthSqr() < 1e-4) {
            right = fwd.cross(WORLD_X);
        }
        return right.normalize();
    }

    private static float flameWobble(int index, float age, int layer) {
        float x = index * 0.731f + layer * 11.37f;
        float a = (float) Math.sin(x * 5.17f + age * NOISE_SPEED * 3.6f);
        float b = (float) Math.sin(x * 2.11f - age * NOISE_SPEED * 1.7f + 1.7f);
        float c = (float) Math.sin(x * 8.43f + age * NOISE_SPEED * 5.2f + 0.31f);
        return Mth.clamp(0.52f + 0.24f * a + 0.18f * b + 0.06f * c, 0f, 1f);
    }

    private static float breakupThreshold(int index, int layer, int entityId) {
        return breakupHash(index, layer, entityId);
    }

    private static float breakupHash(int index, int layer, int entityId) {
        long n = 0x9E3779B97F4A7C15L;
        n ^= (long) index * 0xBF58476D1CE4E5B9L;
        n ^= (long) layer * 0x94D049BB133111EBL;
        n ^= (long) entityId * 0xD6E8FEB86659FD93L;
        n ^= (n >>> 30);
        n *= 0xBF58476D1CE4E5B9L;
        n ^= (n >>> 27);
        n *= 0x94D049BB133111EBL;
        n ^= (n >>> 31);
        return (n & 0xFFFFFFL) / (float) 0x1000000;
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    @Override
    public boolean shouldRender(FlameRingEntity entity, Frustum camera, double camX, double camY, double camZ) {
        // 环面含俯仰（跟随准星），竖直分量大：AABB 以实体为中心对称，防竖直环被视锥裁剪。
        double r = Math.max(4.0, entity.getRadius() + 1.5);
        return camera.isVisible(new AABB(
            entity.getX() - r, entity.getY() - r, entity.getZ() - r,
            entity.getX() + r, entity.getY() + r, entity.getZ() + r
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(FlameRingEntity entity) {
        return FIRE_RING;
    }
}

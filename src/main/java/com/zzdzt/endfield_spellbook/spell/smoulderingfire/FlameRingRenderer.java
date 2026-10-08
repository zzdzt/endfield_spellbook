package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
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
 * 焚灭大回环斩渲染器（V1 Ribbon Mesh 定版）。
 *
 * <p>环的主体由三层 Ribbon Mesh 承担（外焰光晕 / 主焰轮廓 / 炽核），
 * 粒子编排（{@link FlameRingEntity} clientEmbers）继续负责火焰细节：
 * <ul>
 *     <li>揭示阶段：只绘制 startAngle -> revealedDegrees() 的弧段，环头=剑尖（headBoost 增亮）；</li>
 *     <li>自传播阶段：弧段继续增长到 360°；</li>
 *     <li>满环阶段：保持连续火焰带；</li>
 *     <li>溃散阶段：按稳定随机阈值切断 Ribbon，残片沿切向 + 径向甩散。</li>
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

    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_X = new Vec3(1, 0, 0);

    // ---------- 几何（三层带宽已放大 50%：0.34/0.22/0.10 → 0.51/0.33/0.15） ----------
    private static final float SEGMENT_LENGTH = 0.20f;
    private static final float OUTER_WIDTH = 0.51f;
    private static final float BODY_WIDTH = 0.33f;
    private static final float CORE_WIDTH = 0.15f;
    private static final float BREAK_DISTANCE = 0.75f;

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

        VertexConsumer outer = bufferSource.getBuffer(RenderType.entityTranslucent(FIRE_RING));
        VertexConsumer body = bufferSource.getBuffer(RenderType.entityTranslucent(FIRE_RING));
        VertexConsumer core = bufferSource.getBuffer(RenderType.entityTranslucent(FIRE_RING));

        poseStack.pushPose();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // 三层染色 = 莱万汀特效采样色标（build/tex_opt/sample_laevatain.py，h1_680.png 12.7万火系像素）：
        // 外焰=次暗绯红 #AE4535 / 主焰=主体橘红 #EC693A / 炽核=炽白 #FFD78F。
        renderLayer(entity, pose, normal, outer, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, OUTER_WIDTH, 0.68f, 0.27f, 0.21f, burnAlpha, 0, planeNormal);
        renderLayer(entity, pose, normal, body, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, BODY_WIDTH, 0.92f, 0.41f, 0.23f, burnAlpha * 0.95f, 1, planeNormal);
        renderLayer(entity, pose, normal, core, center, fwd, right, theta0, dir,
            revealDeg, radius, segments, age, breakup, CORE_WIDTH, 1.0f, 0.84f, 0.56f, burnAlpha * 0.72f, 2, planeNormal);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
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
        float scroll = age * UV_SCROLL_PER_TICK;

        for (int i = 0; i < segments; i++) {
            float s0 = i / (float) segments;
            float s1 = (i + 1) / (float) segments;
            float a0 = revealDeg * s0;
            float a1 = revealDeg * s1;

            // 溃散：每一段有固定的“碎裂阈值”，避免每帧闪烁。
            float breakThreshold = breakupThreshold(i, layer, entity.getId());
            if (breakup > 0f && breakThreshold < breakup) {
                continue;
            }

            float mid = (s0 + s1) * 0.5f;
            float localAge = age + layer * 1.37f;
            float wobble = proceduralWobble(i, localAge);
            float headDistance = (1f - mid) * revealedArc;
            float headBoost = revealDeg < 359f
                ? (float) Math.exp(-Math.pow(headDistance / HEAD_BOOST_LENGTH, 2.0))
                : 0f;

            float widthMul = 1f + 0.35f * wobble + 0.85f * headBoost;
            if (breakup > 0f) {
                widthMul *= 1f - breakup * 0.25f;
            }
            float halfWidth = width * Math.max(0.45f, widthMul) * 0.5f;

            Vec3 p0 = pointOnArc(center, fwd, right, theta0 + dir * a0, radius);
            Vec3 p1 = pointOnArc(center, fwd, right, theta0 + dir * a1, radius);
            Vec3 radial0 = radial(fwd, right, theta0 + dir * a0);
            Vec3 radial1 = radial(fwd, right, theta0 + dir * a1);
            Vec3 tangent = arcTangent(fwd, right, theta0 + dir * (a0 + a1) * 0.5f);

            // 火环碎裂时，残片向切向甩 + 少量径向外扩。
            if (breakup > 0f) {
                float shard = breakup * breakup;
                float hash = breakupHash(i, layer, entity.getId());
                float tangential = BREAK_DISTANCE * shard * (0.35f + 0.9f * hash);
                float radialOffset = BREAK_DISTANCE * shard * (hash - 0.35f) * 0.45f;
                Vec3 offset = tangent.scale(tangential).add(radial0.scale(radialOffset));
                p0 = p0.add(offset);
                p1 = p1.add(offset);
            }

            double depthOffset = 0.003 + layer * 0.004;
            Vec3 depth = planeNormal.scale(depthOffset);
            Vec3 q0Outer = p0.add(radial0.scale(halfWidth)).add(depth);
            Vec3 q0Inner = p0.subtract(radial0.scale(halfWidth)).add(depth);
            Vec3 q1Outer = p1.add(radial1.scale(halfWidth)).add(depth);
            Vec3 q1Inner = p1.subtract(radial1.scale(halfWidth)).add(depth);

            float u0 = fract(s0 * U_TILING + scroll);
            float u1 = fract(s1 * U_TILING + scroll);
            float vTop = 0f;
            float vBottom = 1f;

            // 让环头更亮，让“剑尖即环头”不仅体现在位置，也体现在亮度。
            float headAlpha = 1f + headBoost * 1.25f;
            float a = Mth.clamp(alpha * headAlpha, 0f, 1f);
            if (breakup > 0f) {
                a *= 0.35f + 0.65f * (1f - breakup);
            }

            putVertex(consumer, pose, normal, q0Outer, u0, vTop, r, g, b, a);
            putVertex(consumer, pose, normal, q0Inner, u0, vBottom, r, g, b, a * 0.75f);
            putVertex(consumer, pose, normal, q1Inner, u1, vBottom, r, g, b, a * 0.75f);
            putVertex(consumer, pose, normal, q1Outer, u1, vTop, r, g, b, a);
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
        consumer.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normal, 0f, 1f, 0f)
            .endVertex();
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

    private static float proceduralWobble(int index, float age) {
        float x = index * 12.9898f + age * NOISE_SPEED * 17.1f;
        return (float) Math.sin(x * 1.731f) * 0.5f + 0.5f;
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

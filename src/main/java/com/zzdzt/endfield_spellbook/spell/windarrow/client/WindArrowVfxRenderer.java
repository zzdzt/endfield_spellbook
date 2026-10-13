package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 风矢穿林独立视觉渲染器。
 *
 * <p>只处理主束、拖尾、卷风弧带、星座光链与命中爆闪；不负责射线命中或伤害结算。
 * 原 LizhiYanBlastRenderer 只保留 STYLE_BLAST 默认渲染与样式分发。
 */
public final class WindArrowVfxRenderer {
    private static final ResourceLocation TEXTURE_WHITE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    private static final RenderType WIND_ARROW_GLOW =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("wind_arrow_glow", TEXTURE_WHITE);

    private static final ResourceLocation TEXTURE_WIND_ARROW_NEEDLE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/particle/wind_arrow_needle.png");
    private static final RenderType WIND_ARROW_NEEDLE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_needle", TEXTURE_WIND_ARROW_NEEDLE, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_BEAM =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_beam.png");
    private static final RenderType WIND_ARROW_BEAM =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_beam", TEXTURE_WIND_ARROW_BEAM, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_RING =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_swirl_trail.png");
    private static final RenderType WIND_ARROW_RING =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_ring", TEXTURE_WIND_ARROW_RING, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_CONSTELLATION =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_constellation.png");
    private static final RenderType WIND_ARROW_CONSTELLATION =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_constellation", TEXTURE_WIND_ARROW_CONSTELLATION, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_IMPACT =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_impact.png");
    private static final RenderType WIND_ARROW_IMPACT =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_impact", TEXTURE_WIND_ARROW_IMPACT, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_SPRITES =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_star_glint.png");
    private static final RenderType WIND_ARROW_SPRITES =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_sprites", TEXTURE_WIND_ARROW_SPRITES, false);

    private WindArrowVfxRenderer() {
    }

    public static void render(LizhiYanBlastVisualEntity entity, float partialTicks,
                                 PoseStack poseStack, MultiBufferSource bufferSource) {
        if (entity.distance <= 0.001f) {
            return;
        }

        float age = entity.tickCount + partialTicks;
        float flightProgress = Mth.clamp((age + 0.35f) / 3.35f, 0.0f, 1.0f);
        float visibleLength = Math.min(entity.distance,
            Math.max(0.05f, entity.distance * flightProgress));
        // 拖尾要在常见战斗距离下形成完整层次；固定 6~10 格容易显得像短光点。
        float activeTrailLength = Math.min(visibleLength,
            Mth.clamp(entity.distance * 0.58f, 8.0f, 16.0f));

        float beamAlpha = 1.0f - Mth.clamp((age - 3.45f) / 1.50f, 0.0f, 1.0f);
        float arcAlpha = 1.0f - Mth.clamp((age - 3.65f) / 2.65f, 0.0f, 1.0f);
        float trailAlpha = 1.0f - Mth.clamp((age - 3.0f) / 7.0f, 0.0f, 1.0f);
        if (trailAlpha <= 0.001f) {
            return;
        }

        Vec3 originWorld = new Vec3(
            Mth.lerp(partialTicks, entity.xo, entity.getX()),
            Mth.lerp(partialTicks, entity.yo, entity.getY()),
            Mth.lerp(partialTicks, entity.zo, entity.getZ())
        );
        Vec3 direction = entity.getBlastDirection();
        if (direction.lengthSqr() < 1.0e-6) {
            direction = new Vec3(0.0, 0.0, 1.0);
        } else {
            direction = direction.normalize();
        }
        Vec3 cameraWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        // 关键：先在世界轴坐标中生成 camera-facing ribbon，避免经过 yaw/pitch 旋转后变成侧边线。
        VertexConsumer beam = bufferSource.getBuffer(WIND_ARROW_BEAM);
        // Layer 1: 宽而低亮的蓝色外晕，负责让轨迹在复杂背景上可读。
        renderWindArrowBillboardRibbon(poseStack, beam, originWorld, direction, cameraWorld,
            visibleLength, activeTrailLength, age,
            0.018f, 0.355f, 0.10f, 0.39f, 1.0f, beamAlpha * 0.58f, 0.14f);
        // Layer 2: 饱和青蓝主带，宽度明显小于外晕，保持箭矢方向感。
        renderWindArrowBillboardRibbon(poseStack, beam, originWorld, direction, cameraWorld,
            visibleLength, activeTrailLength * 0.98f, age + 0.7f,
            0.010f, 0.175f, 0.10f, 0.55f, 1.0f, beamAlpha * 0.84f, 0.075f);
        // Layer 3: 窄白蓝能量芯，形成清晰的中心高光，不与外晕混成一团。
        renderWindArrowBillboardRibbon(poseStack, beam, originWorld, direction, cameraWorld,
            visibleLength, activeTrailLength * 0.88f, age + 1.4f,
            0.0025f, 0.052f, 0.72f, 0.91f, 1.0f, beamAlpha * 0.98f, 0.032f);

        // 用户提供的 needle 贴图只作为短拖尾内的锐利白青线芯。
        VertexConsumer needle = bufferSource.getBuffer(WIND_ARROW_NEEDLE);
        renderWindArrowBillboardRibbon(poseStack, needle, originWorld, direction, cameraWorld,
            visibleLength, activeTrailLength * 0.72f, age + 0.3f,
            0.0010f, 0.022f, 0.92f, 0.985f, 1.0f, beamAlpha * 0.88f, 0.014f);

        // 星座图用相机朝向纹理带贯穿已揭示轨迹，星芒节点则在后续局部坐标里单独闪烁。
        VertexConsumer constellation = bufferSource.getBuffer(WIND_ARROW_CONSTELLATION);
        renderWindArrowBillboardRibbon(poseStack, constellation, originWorld, direction, cameraWorld,
            visibleLength, visibleLength, age + 2.0f,
            0.012f, 0.125f, 0.62f, 0.83f, 1.0f, trailAlpha * 0.36f, 0.085f);

        // 弧带与独立星点围绕模拟中的可见轨迹展开。
        poseStack.pushPose();
        poseStack.translate(0, entity.getBoundingBox().getYsize() * 0.5f, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot() - 90.0F));

        VertexConsumer glow = bufferSource.getBuffer(WIND_ARROW_GLOW);
        VertexConsumer ring = bufferSource.getBuffer(WIND_ARROW_RING);
        renderWindArrowRibbonArcs(poseStack, ring, visibleLength, age, arcAlpha);

        VertexConsumer sprites = bufferSource.getBuffer(WIND_ARROW_SPRITES);
        renderWindArrowSprites(poseStack, sprites, visibleLength, age, arcAlpha, trailAlpha);
        renderWindArrowAfterglow(poseStack, glow, sprites, visibleLength, age, trailAlpha);

        // 箭矢前锋：真正具有方向性的尖锐箭头 + 中心星芒。
        // 旧实现只有四角星作为前锋，远距离时容易被误读成普通光束。
        drawWindArrowHead(poseStack, glow, visibleLength, age, beamAlpha);
        drawWindArrowSprite(poseStack, sprites, 0.0f, visibleLength, 0.0f,
            0.052f, 0.082f, 2, age * 21.0f,
            0.96f, 0.99f, 1.0f, beamAlpha * 0.72f);

        int style = entity.getVisualStyle();
        if (style == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_HIT && age >= 3.0f) {
            float impactAge = age - 3.0f;
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WIND_ARROW_IMPACT),
                0, entity.distance, 0, impactAge, false);
            drawArrowImpact(poseStack, glow, 0, entity.distance, 0, impactAge, false);
        } else if (style == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_FINAL_HIT && age >= 3.0f) {
            float impactAge = age - 3.0f;
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WIND_ARROW_IMPACT),
                0, entity.distance, 0, impactAge, true);
            drawArrowImpact(poseStack, glow, 0, entity.distance, 0, impactAge, true);
        }

        poseStack.popPose();
    }

    /**
     * 风矢尖锋。用三张交叉的渐缩楔形面构成可辨认的箭头，而不是只用星芒
     * 暗示前锋。沿局部 +Y 方向延伸，与该渲染通道的弹道轴保持一致。
     */
    private static void drawWindArrowHead(PoseStack poseStack, VertexConsumer consumer,
                                          float tipY, float age, float alpha) {
        if (alpha <= 0.001f) return;

        float pulse = 0.92f + 0.08f * Mth.sin(age * 1.7f);
        float length = 0.48f * pulse;
        float halfWidth = 0.175f * pulse;
        float baseY = tipY - length;
        float tipAlpha = alpha * 0.98f;
        float wingAlpha = alpha * 0.84f;
        PoseStack.Pose pose = poseStack.last();

        // 正面楔形：亮白中心，青蓝外缘。
        emitVertex(pose, consumer, 0.0f, tipY, 0.0f, 0.5f, 0.0f,
            0.94f, 0.99f, 1.0f, tipAlpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, -halfWidth, baseY, 0.0f, 0.0f, 1.0f,
            0.20f, 0.72f, 1.0f, wingAlpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, halfWidth, baseY, 0.0f, 1.0f, 1.0f,
            0.20f, 0.72f, 1.0f, wingAlpha, 0.0f, 0.0f, 1.0f);

        // 侧向交叉楔形，避免从俯视或侧视时箭头完全变成一条线。
        emitVertex(pose, consumer, 0.0f, tipY, 0.0f, 0.5f, 0.0f,
            0.80f, 0.94f, 1.0f, tipAlpha * 0.92f, 1.0f, 0.0f, 0.0f);
        emitVertex(pose, consumer, 0.0f, baseY, -halfWidth * 0.72f, 0.0f, 1.0f,
            0.12f, 0.52f, 1.0f, wingAlpha * 0.82f, 1.0f, 0.0f, 0.0f);
        emitVertex(pose, consumer, 0.0f, baseY, halfWidth * 0.72f, 1.0f, 1.0f,
            0.12f, 0.52f, 1.0f, wingAlpha * 0.82f, 1.0f, 0.0f, 0.0f);

        // 短白芯将箭头尖端与蓝色主束视觉连接起来。
        float coreWidth = halfWidth * 0.19f;
        float coreBase = tipY - length * 0.72f;
        emitVertex(pose, consumer, 0.0f, tipY + 0.012f, 0.001f, 0.5f, 0.0f,
            1.0f, 1.0f, 1.0f, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, -coreWidth, coreBase, 0.001f, 0.0f, 1.0f,
            0.72f, 0.91f, 1.0f, alpha * 0.92f, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, coreWidth, coreBase, 0.001f, 1.0f, 1.0f,
            0.72f, 0.91f, 1.0f, alpha * 0.92f, 0.0f, 0.0f, 1.0f);
    }

    /**
     * 相机朝向的渐缩轨迹带。区别于固定平面贴片：side vector 随观看方向求解，
     * 镜头从正面、侧面或俯视时都能看见带宽。沿长度采样微小 S 曲线，尾端透明、前锋更亮。
     */
    private static void renderWindArrowBillboardRibbon(PoseStack poseStack, VertexConsumer consumer,
                                                       Vec3 originWorld, Vec3 direction, Vec3 cameraWorld,
                                                       float headDistance, float trailLength, float age,
                                                       float tailHalfWidth, float headHalfWidth,
                                                       float red, float green, float blue,
                                                       float alpha, float curveAmplitude) {
        if (headDistance <= 0.01f || trailLength <= 0.01f || alpha <= 0.001f) {
            return;
        }

        float length = Math.min(headDistance, trailLength);
        float tailStart = Math.max(0.0f, headDistance - length);
        int segments = Mth.clamp((int) Math.ceil(length * 12.0f), 24, 96);
        PoseStack.Pose pose = poseStack.last();

        Vec3 previousSide = null;
        Vec3[] centers = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float along = tailStart + length * t;
            Vec3 baseCenter = direction.scale(along);
            Vec3 centerWorld = originWorld.add(baseCenter);

            Vec3 view = cameraWorld.subtract(centerWorld);
            if (view.lengthSqr() < 1.0e-6) {
                view = new Vec3(0.0, 0.0, 1.0);
            } else {
                view = view.normalize();
            }
            Vec3 side = direction.cross(view);
            if (side.lengthSqr() < 1.0e-6) {
                side = direction.cross(new Vec3(0.0, 1.0, 0.0));
            }
            if (side.lengthSqr() < 1.0e-6) {
                side = direction.cross(new Vec3(1.0, 0.0, 0.0));
            }
            side = side.normalize();
            if (previousSide != null && side.dot(previousSide) < 0.0) {
                side = side.scale(-1.0);
            }
            previousSide = side;
            Vec3 normal = direction.cross(side).normalize();

            // 两端回到主轴，中段产生一至两次轻微偏移，形成流动风迹而非折线弹道。
            float envelope = Mth.sin((float) Math.PI * t);
            float sideWave = Mth.sin(t * (float) Math.PI * 2.0f + age * 0.55f);
            float normalWave = Mth.sin(t * (float) Math.PI * 3.0f - age * 0.37f + 0.8f);
            Vec3 offset = side.scale(curveAmplitude * sideWave * envelope)
                .add(normal.scale(curveAmplitude * 0.46f * normalWave * envelope));
            centers[i] = baseCenter.add(offset);
        }

        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            Vec3 c0 = centers[i];
            Vec3 c1 = centers[i + 1];
            Vec3 tangent = c1.subtract(c0).normalize();

            Vec3 centerWorld0 = originWorld.add(c0);
            Vec3 centerWorld1 = originWorld.add(c1);
            Vec3 view0 = cameraWorld.subtract(centerWorld0).normalize();
            Vec3 view1 = cameraWorld.subtract(centerWorld1).normalize();
            Vec3 side0 = tangent.cross(view0);
            Vec3 side1 = tangent.cross(view1);
            if (side0.lengthSqr() < 1.0e-6) side0 = direction.cross(new Vec3(0.0, 1.0, 0.0));
            if (side1.lengthSqr() < 1.0e-6) side1 = direction.cross(new Vec3(0.0, 1.0, 0.0));
            side0 = side0.normalize();
            side1 = side1.normalize();
            if (side0.dot(side1) < 0.0) side1 = side1.scale(-1.0);

            float eased0 = t0 * t0 * (3.0f - 2.0f * t0);
            float eased1 = t1 * t1 * (3.0f - 2.0f * t1);
            float width0 = Mth.lerp(eased0, tailHalfWidth, headHalfWidth);
            float width1 = Mth.lerp(eased1, tailHalfWidth, headHalfWidth);
            // 尾端柔和淡出，但不要把最初一大段压得过暗；旧曲线使拖尾前半段几乎不可见。
            float fade0 = smoothstep(0.0f, 0.11f, t0) * (0.46f + 0.54f * t0);
            float fade1 = smoothstep(0.0f, 0.11f, t1) * (0.46f + 0.54f * t1);
            Vec3 left0 = c0.subtract(side0.scale(width0));
            Vec3 right0 = c0.add(side0.scale(width0));
            Vec3 left1 = c1.subtract(side1.scale(width1));
            Vec3 right1 = c1.add(side1.scale(width1));

            emitVertex(pose, consumer, (float) left0.x, (float) left0.y, (float) left0.z,
                t0, 1.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            emitVertex(pose, consumer, (float) right0.x, (float) right0.y, (float) right0.z,
                t0, 0.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            emitVertex(pose, consumer, (float) right1.x, (float) right1.y, (float) right1.z,
                t1, 0.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
            emitVertex(pose, consumer, (float) left1.x, (float) left1.y, (float) left1.z,
                t1, 1.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
        }
    }

    private static void renderWindArrowSprites(PoseStack poseStack, VertexConsumer consumer,
                                               float length, float age,
                                               float arcAlpha, float trailAlpha) {
        if (length <= 0.05f) {
            return;
        }

        // 星芒沿弧带之间错落分布：主星更亮更大，辅星较暗，避免均匀圆点链。
        float[] starPositions = {0.09f, 0.18f, 0.27f, 0.39f, 0.51f, 0.63f, 0.76f, 0.89f, 0.96f};
        for (int i = 0; i < starPositions.length; i++) {
            float y = length * starPositions[i];
            float phase = age * 0.43f + i * 2.21f;
            float radius = (i % 3 == 0 ? 0.34f : (i % 2 == 0 ? 0.23f : 0.16f));
            float x = (float) Math.cos(phase * 0.82f + i * 0.37f) * radius;
            float z = (float) Math.sin(phase * 0.91f + i * 0.53f) * radius;
            float pulse = 0.22f + 0.78f * (0.5f + 0.5f * Mth.sin(age * 1.85f + i * 2.1f));
            float size = (i % 4 == 1 ? 0.085f : (i % 3 == 0 ? 0.052f : 0.032f))
                * (0.82f + pulse * 0.34f);
            drawWindArrowSprite(poseStack, consumer, x, y, z,
                size, size * (i % 4 == 1 ? 1.36f : 1.0f), i % 4 == 1 ? 2 : 0, phase * 36.0f,
                i % 4 == 1 ? 0.98f : 0.72f,
                i % 4 == 1 ? 0.99f : 0.87f, 1.0f,
                trailAlpha * (i % 4 == 1 ? 0.92f : 0.57f) * pulse);
        }

        // 稀疏锐利闪光：窄白芯配冷蓝外晕，位置与大星芒错开，避免形成均匀的点线链。
        float[] flashPositions = {0.15f, 0.45f, 0.72f, 0.93f};
        for (int i = 0; i < flashPositions.length; i++) {
            float y = length * flashPositions[i];
            float phase = age * 0.47f + i * 1.91f;
            float radius = (i % 2 == 0 ? 0.22f : 0.31f);
            float x = (float) Math.cos(phase) * radius;
            float z = (float) Math.sin(phase * 0.87f + i * 0.4f) * radius;
            float pulse = 0.26f + 0.74f * (0.5f + 0.5f * Mth.sin(age * 1.9f + i * 2.6f));
            drawWindArrowSprite(poseStack, consumer, x, y, z,
                0.115f, 0.024f, 1, phase * 48.0f + i * 31.0f,
                0.78f, 0.93f, 1.0f, trailAlpha * 0.58f * pulse);
        }

        // 少数斜置晶片/长碎光。尺寸很小，避免回到“密集线圈+杂乱刮线”的观感。
        float[] shardPositions = {0.24f, 0.58f, 0.81f};
        for (int i = 0; i < shardPositions.length; i++) {
            float y = length * shardPositions[i];
            float phase = age * 0.31f + i * 2.2f;
            float x = (float) Math.cos(phase) * (0.27f + i * 0.025f);
            float z = (float) Math.sin(phase) * (0.27f + i * 0.025f);
            float fade = arcAlpha * (0.7f + 0.3f * Mth.sin(age * 0.5f + i));
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(phase * 40.0f + i * 65.0f));
            drawWindArrowSpriteAtOrigin(poseStack, consumer,
                0.105f, 0.155f, 3, 0.58f, 0.80f, 1.0f,
                fade * 0.44f);
            poseStack.popPose();
        }
    }

    /**
     * 银白翻卷弧带。专用 swirl 贴图沿每段开口弧的 UV 方向铺展，
     * 由 UV 流动、几何扭转与多层亮度共同产生动态反射，而不是仅靠纯色几何线。
     */
    private static void renderWindArrowRibbonArcs(PoseStack poseStack, VertexConsumer consumer,
                                                  float length, float age, float alpha) {
        if (alpha <= 0.001f || length <= 0.1f) {
            return;
        }

        // 视频参考是少量大幅卷风环：开口弧的空间尺寸远大于主束，并随年龄产生扭转。
        float[] positions = {0.13f, 0.39f, 0.66f, 0.88f};
        float[] radiusX = {0.90f, 1.22f, 1.01f, 1.29f};
        float[] radiusZ = {0.99f, 1.31f, 1.10f, 1.25f};
        float[] starts = {0.18f, 1.43f, 2.79f, 4.07f};
        float[] spans = {4.78f, 4.55f, 4.94f, 4.62f};
        float[] axialTilt = {0.48f, -0.60f, 0.68f, -0.55f};

        for (int i = 0; i < positions.length; i++) {
            float direction = (i % 2 == 0 ? 1.0f : -1.0f);
            float phase = age * direction * (0.14f + i * 0.012f) + i * 1.37f;
            float centerY = length * positions[i] + 0.19f * Mth.sin(age * 0.36f + i * 1.4f);
            float cx = 0.24f * (float) Math.cos(phase * 1.1f + i * 0.6f);
            float cz = 0.22f * (float) Math.sin(phase * 0.9f + i * 0.8f);
            float start = starts[i] + phase * 0.28f;
            float span = spans[i] + 0.22f * Mth.sin(age * 0.31f + i);
            float swayX = 1.0f + 0.09f * Mth.sin(age * 0.42f + i * 1.7f);
            float swayZ = 1.0f + 0.08f * Mth.cos(age * 0.37f + i * 1.2f);
            float fade = alpha * (0.72f + 0.28f * Mth.sin(age * 0.58f + i * 1.6f));

            // 外晕：宽而透明，从蓝晕改为保留轻冷调的银晕。
            drawWindArrowRibbonArc(poseStack, consumer, cx, centerY, cz,
                radiusX[i] * swayX, radiusZ[i] * swayZ, axialTilt[i], phase, start, span,
                0.205f, 0.88f, 0.92f, 1.0f, fade * 0.22f, 48);
            // 贴图主层：贴图实测偏蓝(B/R≈1.4)，顶点色压 G/B 补偿为银白，alpha 微升补亮度。
            drawWindArrowRibbonArc(poseStack, consumer, cx, centerY, cz,
                radiusX[i] * swayX, radiusZ[i] * swayZ, axialTilt[i], phase, start, span,
                0.105f, 1.0f, 0.88f, 0.82f, fade * 0.74f, 48);
            // 锐利白芯略微缩短；顶点色接近纯白，让贴图最亮核心直接爆成银白。
            drawWindArrowRibbonArc(poseStack, consumer, cx, centerY, cz,
                radiusX[i] * swayX, radiusZ[i] * swayZ, axialTilt[i], phase, start + 0.08f, span * 0.96f,
                0.026f, 1.0f, 1.0f, 0.94f, fade * 0.92f, 48);
        }
    }

    /** 沿椭圆路径构建开口 ribbon，而非封闭 torus；宽度沿弹道轴浮动，首尾自然淡出。 */
    private static void drawWindArrowRibbonArc(PoseStack poseStack, VertexConsumer consumer,
                                               float cx, float cy, float cz,
                                               float radiusX, float radiusZ,
                                               float axialTilt, float phase,
                                               float startAngle, float span,
                                               float width, float red, float green,
                                               float blue, float alpha, int steps) {
        if (alpha <= 0.001f) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < steps; i++) {
            float s0 = i / (float) steps;
            float s1 = (i + 1) / (float) steps;
            float a0 = startAngle + span * s0 + phase * 0.08f;
            float a1 = startAngle + span * s1 + phase * 0.08f;
            float x0 = cx + radiusX * (float) Math.cos(a0);
            float z0 = cz + radiusZ * (float) Math.sin(a0);
            float x1 = cx + radiusX * (float) Math.cos(a1);
            float z1 = cz + radiusZ * (float) Math.sin(a1);
            float y0 = cy + axialTilt * (float) Math.sin(a0 + phase);
            float y1 = cy + axialTilt * (float) Math.sin(a1 + phase);

            float taper0 = 0.84f + 0.16f * (float) Math.sin(Math.PI * s0);
            float taper1 = 0.84f + 0.16f * (float) Math.sin(Math.PI * s1);
            float half0 = width * 0.5f * taper0;
            float half1 = width * 0.5f * taper1;
            float endFade0 = Math.min(1.0f, Math.min(s0 * 5.0f, (1.0f - s0) * 5.0f));
            float endFade1 = Math.min(1.0f, Math.min(s1 * 5.0f, (1.0f - s1) * 5.0f));
            float wave0 = 0.84f + 0.16f * Mth.sin(a0 * 2.0f + phase);
            float wave1 = 0.84f + 0.16f * Mth.sin(a1 * 2.0f + phase);
            float alpha0 = alpha * Math.max(0.0f, endFade0) * wave0;
            float alpha1 = alpha * Math.max(0.0f, endFade1) * wave1;

            // 贴图 U 沿弧段完整铺展，V 横跨丝带。独立纹理默认不保证 repeat wrap，
            // 因此 UV 保持在 0..1；流动感由弧线相位和几何扭转提供。
            float u0 = s0;
            float u1 = s1;
            emitVertex(pose, consumer, x0, y0 + half0, z0, u0, 0,
                red, green, blue, alpha0, 0, 0, 1);
            emitVertex(pose, consumer, x0, y0 - half0, z0, u0, 1,
                red, green, blue, alpha0, 0, 0, 1);
            emitVertex(pose, consumer, x1, y1 - half1, z1, u1, 1,
                red, green, blue, alpha1, 0, 0, 1);
            emitVertex(pose, consumer, x1, y1 + half1, z1, u1, 0,
                red, green, blue, alpha1, 0, 0, 1);
        }
    }

    /**
     * 主束退场后的星点连接余痕。节点分布不等距，连线留出间隙，并有轻微横向摆动；
     * 前期低亮叠在光束周围，后期成为清晰但稀疏的蓝色星链。
     */
    private static void renderWindArrowAfterglow(PoseStack poseStack, VertexConsumer glow,
                                                 VertexConsumer sprites, float length,
                                                 float age, float trailAlpha) {
        if (length <= 0.1f || trailAlpha <= 0.001f) {
            return;
        }

        float revealResidual = Mth.clamp((age - 1.25f) / 1.55f, 0.0f, 1.0f);
        float alpha = trailAlpha * (0.12f + 0.48f * revealResidual);
        int nodeCount = Mth.clamp((int) Math.ceil(length / 0.92f) + 1, 8, 22);
        Vec3[] points = new Vec3[nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            float y = length * t;
            float phase = t * 5.8f - age * 0.17f;
            float x = 0.085f * (float) Math.sin(phase) + 0.032f * (float) Math.sin(phase * 2.8f + 0.7f);
            float z = 0.092f * (float) Math.cos(phase * 1.13f) + 0.027f * (float) Math.sin(phase * 2.1f);
            points[i] = new Vec3(x, y, z);
        }

        // 点线链段不完全接触节点，避免出现一根连续的机械电线。
        for (int i = 0; i < nodeCount - 1; i++) {
            Vec3 delta = points[i + 1].subtract(points[i]);
            Vec3 start = points[i].add(delta.scale(0.18));
            Vec3 end = points[i + 1].subtract(delta.scale(0.24));
            float segmentAlpha = alpha * (i % 3 == 0 ? 0.76f : 0.48f);
            drawTubeSegment(poseStack, glow, start, end,
                i % 3 == 0 ? 0.0032f : 0.0021f,
                0.18f, 0.48f, 1.0f, segmentAlpha, 4);
        }

        // 节点亮度有节奏差异；少量节点带星芒，其余仅保留小光点。
        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            float pulse = 0.34f + 0.66f * (0.5f + 0.5f * Mth.sin(age * 1.1f + i * 1.73f));
            float size = (i % 4 == 2 ? 0.047f : 0.025f) * (0.8f + 0.2f * pulse);
            Vec3 p = points[i];
            if (i % 3 == 0) {
                drawWindArrowSprite(poseStack, sprites, (float) p.x, (float) p.y, (float) p.z,
                    size, size * 1.08f, 2, age * 11.0f + i * 23.0f,
                    0.61f, 0.82f, 1.0f, alpha * 0.52f * pulse);
            } else {
                drawWindArrowSprite(poseStack, sprites, (float) p.x, (float) p.y, (float) p.z,
                    size * 0.52f, size * 0.52f, 0, age * 9.0f + i * 17.0f,
                    0.50f, 0.72f, 1.0f, alpha * 0.42f * pulse);
            }
        }
    }

    /** 将 wind_arrow_needle 的原始横向光痕映射为沿 +Y 方向的渐缩贴图光带。 */
    private static void drawNeedleRibbonY(PoseStack poseStack, VertexConsumer consumer,
                                          float length, float halfWidth, float rotationDegrees,
                                          float red, float green, float blue, float alpha) {
        if (length <= 0.01f || alpha <= 0.001f) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        PoseStack.Pose pose = poseStack.last();
        int longitudinalSegments = Mth.clamp((int) Math.ceil(length * 1.4f), 12, 48);

        for (int i = 0; i < longitudinalSegments; i++) {
            float t0 = i / (float) longitudinalSegments;
            float t1 = (i + 1) / (float) longitudinalSegments;
            float y0 = length * t0;
            float y1 = length * t1;
            float taper0 = beamRadiusProfile(t0);
            float taper1 = beamRadiusProfile(t1);
            float a0 = alpha * beamOpacityProfile(t0);
            float a1 = alpha * beamOpacityProfile(t1);
            float x0 = halfWidth * taper0;
            float x1 = halfWidth * taper1;

            emitVertex(pose, consumer, -x0, y0, 0.0f, t0, 1,
                red, green, blue, a0, 0, 0, 1);
            emitVertex(pose, consumer, x0, y0, 0.0f, t0, 0,
                red, green, blue, a0, 0, 0, 1);
            emitVertex(pose, consumer, x1, y1, 0.0f, t1, 0,
                red, green, blue, a1, 0, 0, 1);
            emitVertex(pose, consumer, -x1, y1, 0.0f, t1, 1,
                red, green, blue, a1, 0, 0, 1);
        }
        poseStack.popPose();
    }

    /** 沿弹道轴放置一张横向旋转的星芒/碎片贴图。 */
    private static void drawWindArrowSprite(PoseStack poseStack, VertexConsumer consumer,
                                            float cx, float cy, float cz,
                                            float halfWidth, float halfHeight, int sprite,
                                            float rotationDegrees, float red, float green,
                                            float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        drawWindArrowSpriteAtOrigin(poseStack, consumer, halfWidth, halfHeight, sprite,
            red, green, blue, alpha);
        poseStack.popPose();
    }

    /**
     * 绘制 wind_arrow_star_glint.png 的单格 atlas。
     * atlas 为 4 列、每列占 0.25 的 U 区间；透明边缘依赖线性过滤柔化。
     */
    private static void drawWindArrowSpriteAtOrigin(PoseStack poseStack, VertexConsumer consumer,
                                                    float halfWidth, float halfHeight, int sprite,
                                                    float red, float green, float blue, float alpha) {
        if (sprite < 0 || sprite > 3 || halfWidth <= 0.0f || halfHeight <= 0.0f || alpha <= 0.0f) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite * 0.25f;
        float u1 = u0 + 0.25f;
        // 每个 atlas tile 高度覆盖完整 V；顶底 UV 反向以与现有纹理方向保持一致。
        float v0 = 0.0f;
        float v1 = 1.0f;

        emitVertex(pose, consumer, -halfWidth, -halfHeight, 0.0f, u0, v1,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, halfWidth, -halfHeight, 0.0f, u1, v1,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, halfWidth, halfHeight, 0.0f, u1, v0,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, -halfWidth, halfHeight, 0.0f, u0, v0,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
    }

    /**
     * 贴图版命中爆闪：三张交叉面避免从侧面观看时星芒消失；外层蓝白、内层近白。
     */
    private static void drawArrowImpactTexture(PoseStack poseStack, VertexConsumer consumer,
                                               float cx, float cy, float cz, float impactAge,
                                               boolean finalBurst) {
        float duration = finalBurst ? 5.5f : 3.0f;
        float t = Mth.clamp(impactAge / duration, 0.0f, 1.0f);
        float eased = t * t * (3.0f - 2.0f * t);
        float fade = 1.0f - t;
        float size = finalBurst ? 0.30f + eased * 1.18f : 0.14f + eased * 0.43f;
        float outerAlpha = fade * (finalBurst ? 0.48f : 0.30f);
        float coreAlpha = fade * (finalBurst ? 0.82f : 0.56f);

        for (int plane = 0; plane < 3; plane++) {
            poseStack.pushPose();
            poseStack.translate(cx, cy, cz);
            if (plane == 1) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            } else if (plane == 2) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            drawArrowImpactQuad(poseStack, consumer, size * 1.15f,
                0.40f, 0.66f, 1.0f, outerAlpha);
            drawArrowImpactQuad(poseStack, consumer, size * 0.66f,
                0.88f, 0.97f, 1.0f, coreAlpha);
            poseStack.popPose();
        }
    }

    private static void drawArrowImpactQuad(PoseStack poseStack, VertexConsumer consumer,
                                            float size, float red, float green,
                                            float blue, float alpha) {
        if (alpha <= 0.001f || size <= 0.001f) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        emitVertex(pose, consumer, -size, -size, 0.0f, 0.0f, 1.0f,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, size, -size, 0.0f, 1.0f, 1.0f,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, size, size, 0.0f, 1.0f, 0.0f,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, -size, size, 0.0f, 0.0f, 0.0f,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
    }

    private static void drawArrowImpact(PoseStack poseStack, VertexConsumer consumer,
                                        float cx, float cy, float cz, float impactAge,
                                        boolean finalBurst) {
        float duration = finalBurst ? 5.5f : 3.0f;
        float t = Mth.clamp(impactAge / duration, 0.0f, 1.0f);
        float eased = t * t * (3.0f - 2.0f * t);
        float alpha = (finalBurst ? 1.0f : 0.94f) * (1.0f - t);
        float radius = finalBurst ? 0.15f + eased * 1.62f : 0.08f + eased * 0.62f;
        float tube = finalBurst ? 0.034f : 0.022f;

        // 蓝色扩张外缘 + 蓝白内环，普通命中短促，最后一击的放射规模明显更大。
        drawTorus(poseStack, consumer, cx, cy, cz, radius, tube * 1.7f, 0,
            0.06f, 0.23f, 1.0f, alpha * 0.66f, 22, 5);
        drawTorus(poseStack, consumer, cx, cy, cz, radius, tube, 0,
            0.64f, 0.82f, 1.0f, alpha, 22, 4);
        drawTorus(poseStack, consumer, cx, cy, cz, radius * 0.78f, tube * 0.62f, 1,
            0.82f, 0.91f, 1.0f, alpha * 0.72f, 20, 4);
        if (finalBurst) {
            drawTorus(poseStack, consumer, cx, cy, cz, radius * 0.60f, tube * 0.54f, 2,
                0.38f, 0.61f, 1.0f, alpha * 0.78f, 20, 4);
        }

        int rayCount = finalBurst ? 20 : 9;
        float startRadius = finalBurst ? radius * 0.12f : radius * 0.46f;
        float endRadius = finalBurst ? radius * 1.22f : radius * 0.96f;
        for (int i = 0; i < rayCount; i++) {
            float ny = 1.0f - 2.0f * ((i + 0.5f) / rayCount);
            float ring = (float) Math.sqrt(Math.max(0.0f, 1.0f - ny * ny));
            float theta = i * 2.3999632f + 0.37f;
            float nx = ring * (float) Math.cos(theta);
            float nz = ring * (float) Math.sin(theta);
            Vec3 start = new Vec3(cx + nx * startRadius, cy + ny * startRadius, cz + nz * startRadius);
            Vec3 end = new Vec3(cx + nx * endRadius, cy + ny * endRadius, cz + nz * endRadius);
            float rayAlpha = alpha * (finalBurst ? 0.92f : 0.72f);
            drawTubeSegment(poseStack, consumer, start, end,
                finalBurst ? 0.022f : 0.014f,
                finalBurst ? 0.26f : 0.42f,
                finalBurst ? 0.48f : 0.68f,
                1.0f, rayAlpha, 5);
        }
    }

    private static void drawTaperedCylinderY(PoseStack poseStack, VertexConsumer consumer,
                                              float length, float radius,
                                              float red, float green, float blue, float alpha,
                                              int radialSegments) {
        if (length <= 0.001f || radius <= 0.001f || alpha <= 0.001f) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        int longitudinalSegments = Mth.clamp((int) Math.ceil(length * 1.35f), 12, 44);

        for (int j = 0; j < longitudinalSegments; j++) {
            float t0 = j / (float) longitudinalSegments;
            float t1 = (j + 1) / (float) longitudinalSegments;
            float y0 = length * t0;
            float y1 = length * t1;
            float r0 = radius * beamRadiusProfile(t0);
            float r1 = radius * beamRadiusProfile(t1);
            float a0 = alpha * beamOpacityProfile(t0);
            float a1 = alpha * beamOpacityProfile(t1);

            for (int i = 0; i < radialSegments; i++) {
                double angle0 = Math.PI * 2.0 * i / radialSegments;
                double angle1 = Math.PI * 2.0 * (i + 1) / radialSegments;
                float c0 = (float) Math.cos(angle0);
                float s0 = (float) Math.sin(angle0);
                float c1 = (float) Math.cos(angle1);
                float s1 = (float) Math.sin(angle1);

                emitVertex(pose, consumer, c0 * r0, y0, s0 * r0,
                    t0, (float) i / radialSegments, red, green, blue, a0,
                    c0, 0, s0);
                emitVertex(pose, consumer, c1 * r0, y0, s1 * r0,
                    t0, (float) (i + 1) / radialSegments, red, green, blue, a0,
                    c1, 0, s1);
                emitVertex(pose, consumer, c1 * r1, y1, s1 * r1,
                    t1, (float) (i + 1) / radialSegments, red, green, blue, a1,
                    c1, 0, s1);
                emitVertex(pose, consumer, c0 * r1, y1, s0 * r1,
                    t1, (float) i / radialSegments, red, green, blue, a1,
                    c0, 0, s0);
            }
        }
    }

    /** 先保持主体直径稳定，靠近前端时快速收尖；比平头长柱更接近光矢的针状轮廓。 */
    private static float beamRadiusProfile(float t) {
        float tipTaper = 1.0f - 0.82f * smoothstep(0.80f, 1.0f, t);
        float body = 0.82f + 0.18f * Mth.sin(t * (float) Math.PI);
        return Math.max(0.12f, body * tipTaper);
    }

    /** 光束尾端略暗、尖端保持高亮；材质本身再提供横向柔化。 */
    private static float beamOpacityProfile(float t) {
        return Mth.clamp(0.52f + 0.34f * t + 0.14f * Mth.sin(t * (float) Math.PI), 0.0f, 1.0f);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** plane=0: normal Y; plane=1: normal X; plane=2: normal Z. */
    private static void drawTorus(PoseStack poseStack, VertexConsumer consumer,
                                  float cx, float cy, float cz,
                                  float radius, float tubeRadius, int plane,
                                  float red, float green, float blue, float alpha,
                                  int majorSegments, int tubeSegments) {
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < majorSegments; i++) {
            double theta0 = Math.PI * 2.0 * i / majorSegments;
            double theta1 = Math.PI * 2.0 * (i + 1) / majorSegments;
            for (int j = 0; j < tubeSegments; j++) {
                double phi0 = Math.PI * 2.0 * j / tubeSegments;
                double phi1 = Math.PI * 2.0 * (j + 1) / tubeSegments;
                emitTorusVertex(pose, consumer, cx, cy, cz, radius, tubeRadius, plane,
                    theta0, phi0, (float) i / majorSegments, (float) j / tubeSegments,
                    red, green, blue, alpha);
                emitTorusVertex(pose, consumer, cx, cy, cz, radius, tubeRadius, plane,
                    theta1, phi0, (float) (i + 1) / majorSegments, (float) j / tubeSegments,
                    red, green, blue, alpha);
                emitTorusVertex(pose, consumer, cx, cy, cz, radius, tubeRadius, plane,
                    theta1, phi1, (float) (i + 1) / majorSegments, (float) (j + 1) / tubeSegments,
                    red, green, blue, alpha);
                emitTorusVertex(pose, consumer, cx, cy, cz, radius, tubeRadius, plane,
                    theta0, phi1, (float) i / majorSegments, (float) (j + 1) / tubeSegments,
                    red, green, blue, alpha);
            }
        }
    }

    private static void emitTorusVertex(PoseStack.Pose pose, VertexConsumer consumer,
                                        float cx, float cy, float cz,
                                        float radius, float tubeRadius, int plane,
                                        double theta, double phi, float u, float v,
                                        float red, float green, float blue, float alpha) {
        double radial = radius + tubeRadius * Math.cos(phi);
        double tube = tubeRadius * Math.sin(phi);
        float x;
        float y;
        float z;
        float nx;
        float ny;
        float nz;
        if (plane == 0) {
            x = cx + (float) (radial * Math.cos(theta));
            y = cy + (float) tube;
            z = cz + (float) (radial * Math.sin(theta));
            nx = (float) (Math.cos(phi) * Math.cos(theta));
            ny = (float) Math.sin(phi);
            nz = (float) (Math.cos(phi) * Math.sin(theta));
        } else if (plane == 1) {
            x = cx + (float) tube;
            y = cy + (float) (radial * Math.cos(theta));
            z = cz + (float) (radial * Math.sin(theta));
            nx = (float) Math.sin(phi);
            ny = (float) (Math.cos(phi) * Math.cos(theta));
            nz = (float) (Math.cos(phi) * Math.sin(theta));
        } else {
            x = cx + (float) (radial * Math.cos(theta));
            y = cy + (float) (radial * Math.sin(theta));
            z = cz + (float) tube;
            nx = (float) (Math.cos(phi) * Math.cos(theta));
            ny = (float) (Math.cos(phi) * Math.sin(theta));
            nz = (float) Math.sin(phi);
        }
        emitVertex(pose, consumer, x, y, z, u, v, red, green, blue, alpha, nx, ny, nz);
    }

    private static void drawTubeSegment(PoseStack poseStack, VertexConsumer consumer,
                                        Vec3 start, Vec3 end, float radius,
                                        float red, float green, float blue, float alpha,
                                        int segments) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 1.0e-8) {
            return;
        }
        Vec3 direction = delta.normalize();
        Vec3 reference = Math.abs(direction.y) < 0.9
            ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = direction.cross(reference).normalize();
        Vec3 up = direction.cross(right).normalize();
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 o0 = right.scale(Math.cos(a0) * radius).add(up.scale(Math.sin(a0) * radius));
            Vec3 o1 = right.scale(Math.cos(a1) * radius).add(up.scale(Math.sin(a1) * radius));
            Vec3 p0 = start.add(o0);
            Vec3 p1 = start.add(o1);
            Vec3 p2 = end.add(o1);
            Vec3 p3 = end.add(o0);
            float u0 = (float) i / segments;
            float u1 = (float) (i + 1) / segments;
            emitVertex(pose, consumer, (float) p0.x, (float) p0.y, (float) p0.z, u0, 0,
                red, green, blue, alpha, (float) (o0.x / radius), (float) (o0.y / radius), (float) (o0.z / radius));
            emitVertex(pose, consumer, (float) p1.x, (float) p1.y, (float) p1.z, u1, 0,
                red, green, blue, alpha, (float) (o1.x / radius), (float) (o1.y / radius), (float) (o1.z / radius));
            emitVertex(pose, consumer, (float) p2.x, (float) p2.y, (float) p2.z, u1, 1,
                red, green, blue, alpha, (float) (o1.x / radius), (float) (o1.y / radius), (float) (o1.z / radius));
            emitVertex(pose, consumer, (float) p3.x, (float) p3.y, (float) p3.z, u0, 1,
                red, green, blue, alpha, (float) (o0.x / radius), (float) (o0.y / radius), (float) (o0.z / radius));
        }
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer,
                                   float x, float y, float z, float u, float v,
                                   float red, float green, float blue, float alpha,
                                   float nx, float ny, float nz) {
        consumer.vertex(pose.pose(), x, y, z)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(pose.normal(), nx, ny, nz)
            .endVertex();
    }
}

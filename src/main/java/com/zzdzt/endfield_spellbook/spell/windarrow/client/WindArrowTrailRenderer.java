package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 风矢移动轨迹层：主束 billboard 丝带、卷风弧带、星芒碎光与星链余痕。
 *
 * <p>renderMainRibbon 在世界轴坐标生成相机朝向丝带；其余三个通道要求调用方
 * 已进入弹道局部坐标系（由 {@link WindArrowVfxRenderer} 的编排统一推拉 pose）。
 */
public final class WindArrowTrailRenderer {

    private WindArrowTrailRenderer() {
    }

    /**
     * 主束四层 billboard 丝带：外晕、主带、能量芯与 needle 白芯，外加贯穿全程的星座带。
     */
    public static void renderMainRibbon(WindArrowRenderState state, PoseStack poseStack,
                                        MultiBufferSource bufferSource) {
        VertexConsumer beam = bufferSource.getBuffer(WindArrowRenderTypes.BEAM);
        // Layer 1: 宽而低亮的蓝色外晕，负责让轨迹在复杂背景上可读。
        renderWindArrowBillboardRibbon(poseStack, beam, state.originWorld, state.direction,
            state.cameraWorld, state.visibleLength, state.activeTrailLength, state.age,
            0.018f, 0.355f, 0.10f, 0.39f, 1.0f, state.beamAlpha * 0.58f, 0.14f);
        // Layer 2: 饱和青蓝主带，宽度明显小于外晕，保持箭矢方向感。
        renderWindArrowBillboardRibbon(poseStack, beam, state.originWorld, state.direction,
            state.cameraWorld, state.visibleLength, state.activeTrailLength * 0.98f, state.age + 0.7f,
            0.010f, 0.175f, 0.10f, 0.55f, 1.0f, state.beamAlpha * 0.84f, 0.075f);
        // Layer 3: 窄白蓝能量芯，形成清晰的中心高光，不与外晕混成一团。
        renderWindArrowBillboardRibbon(poseStack, beam, state.originWorld, state.direction,
            state.cameraWorld, state.visibleLength, state.activeTrailLength * 0.88f, state.age + 1.4f,
            0.0025f, 0.052f, 0.72f, 0.91f, 1.0f, state.beamAlpha * 0.98f, 0.032f);

        // 用户提供的 needle 贴图只作为短拖尾内的锐利白青线芯。
        VertexConsumer needle = bufferSource.getBuffer(WindArrowRenderTypes.NEEDLE);
        renderWindArrowBillboardRibbon(poseStack, needle, state.originWorld, state.direction,
            state.cameraWorld, state.visibleLength, state.activeTrailLength * 0.72f, state.age + 0.3f,
            0.0010f, 0.022f, 0.92f, 0.985f, 1.0f, state.beamAlpha * 0.88f, 0.014f);

        // 星座图用相机朝向纹理带贯穿已揭示轨迹，星芒节点则在后续局部坐标里单独闪烁。
        VertexConsumer constellation = bufferSource.getBuffer(WindArrowRenderTypes.CONSTELLATION);
        renderWindArrowBillboardRibbon(poseStack, constellation, state.originWorld, state.direction,
            state.cameraWorld, state.visibleLength, state.visibleLength, state.age + 2.0f,
            0.012f, 0.125f, 0.62f, 0.83f, 1.0f, state.trailAlpha * 0.36f, 0.085f);
    }

    /** 银白翻卷弧带：swirl 贴图沿四组开口椭圆弧铺展。 */
    public static void renderSwirlTrail(WindArrowRenderState state, PoseStack poseStack,
                                        MultiBufferSource bufferSource) {
        VertexConsumer ring = bufferSource.getBuffer(WindArrowRenderTypes.RING);
        renderWindArrowRibbonArcs(poseStack, ring, state.visibleLength, state.age, state.arcAlpha);
    }

    /** 星芒、锐利闪光与斜置晶片。 */
    public static void renderStarGlint(WindArrowRenderState state, PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        VertexConsumer sprites = bufferSource.getBuffer(WindArrowRenderTypes.SPRITES);
        VertexConsumer shards = bufferSource.getBuffer(WindArrowRenderTypes.SHARDS);
        renderWindArrowSprites(poseStack, sprites, shards,
            state.visibleLength, state.age, state.arcAlpha, state.trailAlpha);
    }

    /** 主束退场后的星点连接余痕。 */
    public static void renderAfterglow(WindArrowRenderState state, PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        VertexConsumer glow = bufferSource.getBuffer(WindArrowRenderTypes.GLOW);
        VertexConsumer sprites = bufferSource.getBuffer(WindArrowRenderTypes.SPRITES);
        renderWindArrowAfterglow(poseStack, glow, sprites,
            state.visibleLength, state.age, state.trailAlpha);
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

            WindArrowRenderUtil.emitVertex(pose, consumer, (float) left0.x, (float) left0.y, (float) left0.z,
                t0, 1.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) right0.x, (float) right0.y, (float) right0.z,
                t0, 0.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) right1.x, (float) right1.y, (float) right1.z,
                t1, 0.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) left1.x, (float) left1.y, (float) left1.z,
                t1, 1.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
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
            WindArrowRenderUtil.emitVertex(pose, consumer, x0, y0 + half0, z0, u0, 0,
                red, green, blue, alpha0, 0, 0, 1);
            WindArrowRenderUtil.emitVertex(pose, consumer, x0, y0 - half0, z0, u0, 1,
                red, green, blue, alpha0, 0, 0, 1);
            WindArrowRenderUtil.emitVertex(pose, consumer, x1, y1 - half1, z1, u1, 1,
                red, green, blue, alpha1, 0, 0, 1);
            WindArrowRenderUtil.emitVertex(pose, consumer, x1, y1 + half1, z1, u1, 0,
                red, green, blue, alpha1, 0, 0, 1);
        }
    }

    private static void renderWindArrowSprites(PoseStack poseStack, VertexConsumer consumer,
                                               VertexConsumer shardConsumer,
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
            WindArrowRenderUtil.drawSprite(poseStack, consumer, x, y, z,
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
            WindArrowRenderUtil.drawSprite(poseStack, consumer, x, y, z,
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
            // 独立晶体图集：四格分别提供细长切面、弧形碎片、紧凑晶片与双碎片组合。
            // 近银白 tint 保留贴图高光层次，强蓝色由独立外晕承担。
            WindArrowRenderUtil.drawSpriteAtOrigin(poseStack, shardConsumer,
                0.105f, 0.155f, i % 4, 0.86f, 0.96f, 1.0f,
                fade * 0.54f);
            poseStack.popPose();
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
                WindArrowRenderUtil.drawSprite(poseStack, sprites, (float) p.x, (float) p.y, (float) p.z,
                    size, size * 1.08f, 2, age * 11.0f + i * 23.0f,
                    0.61f, 0.82f, 1.0f, alpha * 0.52f * pulse);
            } else {
                WindArrowRenderUtil.drawSprite(poseStack, sprites, (float) p.x, (float) p.y, (float) p.z,
                    size * 0.52f, size * 0.52f, 0, age * 9.0f + i * 17.0f,
                    0.50f, 0.72f, 1.0f, alpha * 0.42f * pulse);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** plane=0: normal Y; plane=1: normal X; plane=2: normal Z. */
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
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p0.x, (float) p0.y, (float) p0.z, u0, 0,
                red, green, blue, alpha, (float) (o0.x / radius), (float) (o0.y / radius), (float) (o0.z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p1.x, (float) p1.y, (float) p1.z, u1, 0,
                red, green, blue, alpha, (float) (o1.x / radius), (float) (o1.y / radius), (float) (o1.z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p2.x, (float) p2.y, (float) p2.z, u1, 1,
                red, green, blue, alpha, (float) (o1.x / radius), (float) (o1.y / radius), (float) (o1.z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p3.x, (float) p3.y, (float) p3.z, u0, 1,
                red, green, blue, alpha, (float) (o0.x / radius), (float) (o0.y / radius), (float) (o0.z / radius));
        }
    }
}

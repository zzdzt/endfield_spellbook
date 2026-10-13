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
 * <p>主束在世界轴坐标生成相机朝向丝带；卷风弧带与星链自行进入弹道局部坐标系绘制。
 */
public final class WindArrowTrailRenderer {

    private WindArrowTrailRenderer() {
    }

    // swirl 弧带与星点排布常量：每帧内容不变，避免热路径重复分配数组。
    private static final float[] SWIRL_POSITIONS = {0.13f, 0.39f, 0.66f, 0.88f};
    private static final float[] SWIRL_RADIUS_X = {0.90f, 1.22f, 1.01f, 1.29f};
    private static final float[] SWIRL_RADIUS_Z = {0.99f, 1.31f, 1.10f, 1.25f};
    private static final float[] SWIRL_STARTS = {0.18f, 1.43f, 2.79f, 4.07f};
    private static final float[] SWIRL_SPANS = {4.78f, 4.55f, 4.94f, 4.62f};
    private static final float[] SWIRL_TILTS = {0.48f, -0.60f, 0.68f, -0.55f};
    private static final float[] STAR_POSITIONS = {0.09f, 0.18f, 0.27f, 0.39f, 0.51f, 0.63f, 0.76f, 0.89f, 0.96f};
    private static final float[] FLASH_POSITIONS = {0.15f, 0.45f, 0.72f, 0.93f};
    private static final float[] SHARD_POSITIONS = {0.24f, 0.58f, 0.81f};

    // 热路径原语缓冲：MC 渲染在 RenderThread 单线程执行，复用同一数组避免每帧每层重复分配。
    // billboard segments 上限 96（Mth.clamp 保证），afterglow 节点上限 22。
    private static final double[] RIBBON_CENTER_X = new double[97];
    private static final double[] RIBBON_CENTER_Y = new double[97];
    private static final double[] RIBBON_CENTER_Z = new double[97];
    private static final double[] AFTERGLOW_NODE_X = new double[22];
    private static final double[] AFTERGLOW_NODE_Y = new double[22];
    private static final double[] AFTERGLOW_NODE_Z = new double[22];

    /** 轨迹层统一入口：世界轴主束 + 弹道局部坐标下的弧带与星链。 */
    public static void render(WindArrowRenderState state, WindArrowRenderContext context) {
        PoseStack poseStack = context.poseStack;
        MultiBufferSource bufferSource = context.bufferSource;
        renderMainRibbon(state, poseStack, bufferSource);

        WindArrowRenderUtil.pushTrajectoryPose(poseStack, state);
        renderSwirlTrail(state, poseStack, bufferSource);
        renderStarGlint(state, poseStack, bufferSource);
        renderAfterglow(state, poseStack, bufferSource);
        poseStack.popPose();
    }

    /**
     * 主束四层 billboard 丝带：外晕、主带、能量芯与 needle 白芯，外加贯穿全程的星座带。
     */
    private static void renderMainRibbon(WindArrowRenderState state, PoseStack poseStack,
                                         MultiBufferSource bufferSource) {
        VertexConsumer beam = bufferSource.getBuffer(WindArrowRenderTypes.BEAM);
        // Layer 1: 宽而低亮的蓝色外晕，负责让轨迹在复杂背景上可读。
        renderWindArrowBillboardRibbon(poseStack, beam, state, state.activeTrailLength, 0.0f,
            0.018f, 0.355f, 0.10f, 0.39f, 1.0f, state.beamAlpha * 0.58f, 0.14f);
        // Layer 2: 饱和青蓝主带，宽度明显小于外晕，保持箭矢方向感。
        renderWindArrowBillboardRibbon(poseStack, beam, state, state.activeTrailLength * 0.98f, 0.7f,
            0.010f, 0.175f, 0.10f, 0.55f, 1.0f, state.beamAlpha * 0.84f, 0.075f);
        // Layer 3: 窄白蓝能量芯，形成清晰的中心高光，不与外晕混成一团。
        renderWindArrowBillboardRibbon(poseStack, beam, state, state.activeTrailLength * 0.88f, 1.4f,
            0.0025f, 0.052f, 0.72f, 0.91f, 1.0f, state.beamAlpha * 0.98f, 0.032f);

        // 用户提供的 needle 贴图只作为短拖尾内的锐利白青线芯。
        VertexConsumer needle = bufferSource.getBuffer(WindArrowRenderTypes.NEEDLE);
        renderWindArrowBillboardRibbon(poseStack, needle, state, state.activeTrailLength * 0.72f, 0.3f,
            0.0010f, 0.022f, 0.92f, 0.985f, 1.0f, state.beamAlpha * 0.88f, 0.014f);

        // 星座图用相机朝向纹理带贯穿已揭示轨迹，星芒节点则在后续局部坐标里单独闪烁。
        VertexConsumer constellation = bufferSource.getBuffer(WindArrowRenderTypes.CONSTELLATION);
        renderWindArrowBillboardRibbon(poseStack, constellation, state, state.visibleLength, 2.0f,
            0.012f, 0.125f, 0.62f, 0.83f, 1.0f, state.trailAlpha * 0.36f, 0.085f);
    }

    /** 银白翻卷弧带：swirl 贴图沿四组开口椭圆弧铺展。 */
    private static void renderSwirlTrail(WindArrowRenderState state, PoseStack poseStack,
                                        MultiBufferSource bufferSource) {
        VertexConsumer ring = bufferSource.getBuffer(WindArrowRenderTypes.RING);
        renderWindArrowRibbonArcs(poseStack, ring, state);
    }

    /** 星芒、锐利闪光与斜置晶片。 */
    private static void renderStarGlint(WindArrowRenderState state, PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        VertexConsumer sprites = bufferSource.getBuffer(WindArrowRenderTypes.SPRITES);
        VertexConsumer shards = bufferSource.getBuffer(WindArrowRenderTypes.SHARDS);
        renderWindArrowSprites(poseStack, sprites, shards, state);
    }

    /** 主束退场后的星点连接余痕。 */
    private static void renderAfterglow(WindArrowRenderState state, PoseStack poseStack,
                                       MultiBufferSource bufferSource) {
        VertexConsumer glow = bufferSource.getBuffer(WindArrowRenderTypes.GLOW);
        VertexConsumer sprites = bufferSource.getBuffer(WindArrowRenderTypes.SPRITES);
        renderWindArrowAfterglow(poseStack, glow, sprites, state);
    }

    /**
     * 相机朝向的渐缩轨迹带。区别于固定平面贴片：side vector 随观看方向求解，
     * 镜头从正面、侧面或俯视时都能看见带宽。沿长度采样微小 S 曲线，尾端透明、前锋更亮。
     *
     * <p>热路径全标量实现：与 Vec3 逐位同序的公式展开，循环内零对象分配；
     * normalize 保留 Vec3 的 1.0E-4 零向量语义与原两级 fallback 顺序。
     */
    private static void renderWindArrowBillboardRibbon(PoseStack poseStack, VertexConsumer consumer,
                                                       WindArrowRenderState state,
                                                       float trailLength, float ageShift,
                                                       float tailHalfWidth, float headHalfWidth,
                                                       float red, float green, float blue,
                                                       float alpha, float curveAmplitude) {
        if (state.visibleLength <= 0.01f || trailLength <= 0.01f || alpha <= 0.001f) {
            return;
        }

        Vec3 originWorld = state.originWorld;
        Vec3 direction = state.direction;
        Vec3 cameraWorld = state.cameraWorld;
        float headDistance = state.visibleLength;
        float age = state.age + ageShift;
        float length = Math.min(headDistance, trailLength);
        float tailStart = Math.max(0.0f, headDistance - length);
        int segments = Mth.clamp((int) Math.ceil(length * 12.0f), 24, 96);
        PoseStack.Pose pose = poseStack.last();

        double dirX = direction.x;
        double dirY = direction.y;
        double dirZ = direction.z;
        double camX = cameraWorld.x;
        double camY = cameraWorld.y;
        double camZ = cameraWorld.z;
        double orgX = originWorld.x;
        double orgY = originWorld.y;
        double orgZ = originWorld.z;

        // 中心线：中心点 + S 形偏移，全部标量存储在共享缓冲，避免逐段 Vec3 短命对象。
        double[] centerX = RIBBON_CENTER_X;
        double[] centerY = RIBBON_CENTER_Y;
        double[] centerZ = RIBBON_CENTER_Z;
        double prevSideX = 0.0;
        double prevSideY = 0.0;
        double prevSideZ = 0.0;
        boolean hasPrevSide = false;

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float along = tailStart + length * t;
            double baseX = dirX * along;
            double baseY = dirY * along;
            double baseZ = dirZ * along;
            double worldX = orgX + baseX;
            double worldY = orgY + baseY;
            double worldZ = orgZ + baseZ;

            // view = camera - centerWorld；与原 Vec3 流程一致：短向量取 (0,0,1)。
            double viewX = camX - worldX;
            double viewY = camY - worldY;
            double viewZ = camZ - worldZ;
            double viewLenSqr = viewX * viewX + viewY * viewY + viewZ * viewZ;
            if (viewLenSqr < 1.0e-6) {
                viewX = 0.0;
                viewY = 0.0;
                viewZ = 1.0;
            } else {
                double viewLen = Math.sqrt(viewLenSqr);
                if (viewLen >= 1.0E-4) {
                    viewX /= viewLen;
                    viewY /= viewLen;
                    viewZ /= viewLen;
                } else {
                    viewX = 0.0;
                    viewY = 0.0;
                    viewZ = 0.0;
                }
            }

            // side = direction × view，带两级与原实现相同的退化 fallback。
            double sideX = dirY * viewZ - dirZ * viewY;
            double sideY = dirZ * viewX - dirX * viewZ;
            double sideZ = dirX * viewY - dirY * viewX;
            if (sideX * sideX + sideY * sideY + sideZ * sideZ < 1.0e-6) {
                sideX = -dirZ;
                sideY = 0.0;
                sideZ = dirX;
            }
            if (sideX * sideX + sideY * sideY + sideZ * sideZ < 1.0e-6) {
                sideX = 0.0;
                sideY = dirZ;
                sideZ = -dirY;
            }
            double sideLen = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
            if (sideLen >= 1.0E-4) {
                sideX /= sideLen;
                sideY /= sideLen;
                sideZ /= sideLen;
            } else {
                sideX = 0.0;
                sideY = 0.0;
                sideZ = 0.0;
            }
            if (hasPrevSide && sideX * prevSideX + sideY * prevSideY + sideZ * prevSideZ < 0.0) {
                sideX = -sideX;
                sideY = -sideY;
                sideZ = -sideZ;
            }
            prevSideX = sideX;
            prevSideY = sideY;
            prevSideZ = sideZ;

            double normalX = dirY * sideZ - dirZ * sideY;
            double normalY = dirZ * sideX - dirX * sideZ;
            double normalZ = dirX * sideY - dirY * sideX;
            double normalLen = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (normalLen >= 1.0E-4) {
                normalX /= normalLen;
                normalY /= normalLen;
                normalZ /= normalLen;
            } else {
                normalX = 0.0;
                normalY = 0.0;
                normalZ = 0.0;
            }

            // 两端回到主轴，中段产生一至两次轻微偏移，形成流动风迹而非折线弹道。
            float envelope = Mth.sin((float) Math.PI * t);
            float sideWave = Mth.sin(t * (float) Math.PI * 2.0f + age * 0.55f);
            float normalWave = Mth.sin(t * (float) Math.PI * 3.0f - age * 0.37f + 0.8f);
            double offsetSide = curveAmplitude * sideWave * envelope;
            double offsetNormal = curveAmplitude * 0.46f * normalWave * envelope;
            centerX[i] = baseX + sideX * offsetSide + normalX * offsetNormal;
            centerY[i] = baseY + sideY * offsetSide + normalY * offsetNormal;
            centerZ[i] = baseZ + sideZ * offsetSide + normalZ * offsetNormal;
        }

        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            double c0x = centerX[i];
            double c0y = centerY[i];
            double c0z = centerZ[i];
            double c1x = centerX[i + 1];
            double c1y = centerY[i + 1];
            double c1z = centerZ[i + 1];
            double tangentX = c1x - c0x;
            double tangentY = c1y - c0y;
            double tangentZ = c1z - c0z;
            double tangentLen = Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
            if (tangentLen >= 1.0E-4) {
                tangentX /= tangentLen;
                tangentY /= tangentLen;
                tangentZ /= tangentLen;
            } else {
                tangentX = 0.0;
                tangentY = 0.0;
                tangentZ = 0.0;
            }

            double world0X = orgX + c0x;
            double world0Y = orgY + c0y;
            double world0Z = orgZ + c0z;
            double world1X = orgX + c1x;
            double world1Y = orgY + c1y;
            double world1Z = orgZ + c1z;
            // view0/1 = (camera - centerWorld).normalize()，与原实现一致：无短向量前置检查。
            double view0X = camX - world0X;
            double view0Y = camY - world0Y;
            double view0Z = camZ - world0Z;
            double view0Len = Math.sqrt(view0X * view0X + view0Y * view0Y + view0Z * view0Z);
            if (view0Len >= 1.0E-4) {
                view0X /= view0Len;
                view0Y /= view0Len;
                view0Z /= view0Len;
            } else {
                view0X = 0.0;
                view0Y = 0.0;
                view0Z = 0.0;
            }
            double view1X = camX - world1X;
            double view1Y = camY - world1Y;
            double view1Z = camZ - world1Z;
            double view1Len = Math.sqrt(view1X * view1X + view1Y * view1Y + view1Z * view1Z);
            if (view1Len >= 1.0E-4) {
                view1X /= view1Len;
                view1Y /= view1Len;
                view1Z /= view1Len;
            } else {
                view1X = 0.0;
                view1Y = 0.0;
                view1Z = 0.0;
            }

            double side0X = tangentY * view0Z - tangentZ * view0Y;
            double side0Y = tangentZ * view0X - tangentX * view0Z;
            double side0Z = tangentX * view0Y - tangentY * view0X;
            if (side0X * side0X + side0Y * side0Y + side0Z * side0Z < 1.0e-6) {
                side0X = -dirZ;
                side0Y = 0.0;
                side0Z = dirX;
            }
            double side0Len = Math.sqrt(side0X * side0X + side0Y * side0Y + side0Z * side0Z);
            if (side0Len >= 1.0E-4) {
                side0X /= side0Len;
                side0Y /= side0Len;
                side0Z /= side0Len;
            } else {
                side0X = 0.0;
                side0Y = 0.0;
                side0Z = 0.0;
            }

            double side1X = tangentY * view1Z - tangentZ * view1Y;
            double side1Y = tangentZ * view1X - tangentX * view1Z;
            double side1Z = tangentX * view1Y - tangentY * view1X;
            if (side1X * side1X + side1Y * side1Y + side1Z * side1Z < 1.0e-6) {
                side1X = -dirZ;
                side1Y = 0.0;
                side1Z = dirX;
            }
            double side1Len = Math.sqrt(side1X * side1X + side1Y * side1Y + side1Z * side1Z);
            if (side1Len >= 1.0E-4) {
                side1X /= side1Len;
                side1Y /= side1Len;
                side1Z /= side1Len;
            } else {
                side1X = 0.0;
                side1Y = 0.0;
                side1Z = 0.0;
            }
            if (side0X * side1X + side0Y * side1Y + side0Z * side1Z < 0.0) {
                side1X = -side1X;
                side1Y = -side1Y;
                side1Z = -side1Z;
            }

            float eased0 = t0 * t0 * (3.0f - 2.0f * t0);
            float eased1 = t1 * t1 * (3.0f - 2.0f * t1);
            float width0 = Mth.lerp(eased0, tailHalfWidth, headHalfWidth);
            float width1 = Mth.lerp(eased1, tailHalfWidth, headHalfWidth);
            // 尾端柔和淡出，但不要把最初一大段压得过暗；旧曲线使拖尾前半段几乎不可见。
            float fade0 = smoothstep(0.0f, 0.11f, t0) * (0.46f + 0.54f * t0);
            float fade1 = smoothstep(0.0f, 0.11f, t1) * (0.46f + 0.54f * t1);
            double left0X = c0x - side0X * width0;
            double left0Y = c0y - side0Y * width0;
            double left0Z = c0z - side0Z * width0;
            double right0X = c0x + side0X * width0;
            double right0Y = c0y + side0Y * width0;
            double right0Z = c0z + side0Z * width0;
            double left1X = c1x - side1X * width1;
            double left1Y = c1y - side1Y * width1;
            double left1Z = c1z - side1Z * width1;
            double right1X = c1x + side1X * width1;
            double right1Y = c1y + side1Y * width1;
            double right1Z = c1z + side1Z * width1;

            WindArrowRenderUtil.emitVertex(pose, consumer, (float) left0X, (float) left0Y, (float) left0Z,
                t0, 1.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) right0X, (float) right0Y, (float) right0Z,
                t0, 0.0f, red, green, blue, alpha * fade0, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) right1X, (float) right1Y, (float) right1Z,
                t1, 0.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) left1X, (float) left1Y, (float) left1Z,
                t1, 1.0f, red, green, blue, alpha * fade1, 0.0f, 1.0f, 0.0f);
        }
    }

    /**
     * 银白翻卷弧带。专用 swirl 贴图沿每段开口弧的 UV 方向铺展，
     * 由 UV 流动、几何扭转与多层亮度共同产生动态反射，而不是仅靠纯色几何线。
     */
    private static void renderWindArrowRibbonArcs(PoseStack poseStack, VertexConsumer consumer,
                                                  WindArrowRenderState state) {
        float length = state.visibleLength;
        float age = state.age;
        float alpha = state.arcAlpha;
        if (alpha <= 0.001f || length <= 0.1f) {
            return;
        }

        // 视频参考是少量大幅卷风环：开口弧的空间尺寸远大于主束，并随年龄产生扭转。
        float[] positions = SWIRL_POSITIONS;
        float[] radiusX = SWIRL_RADIUS_X;
        float[] radiusZ = SWIRL_RADIUS_Z;
        float[] starts = SWIRL_STARTS;
        float[] spans = SWIRL_SPANS;
        float[] axialTilt = SWIRL_TILTS;

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
                                               WindArrowRenderState state) {
        float length = state.visibleLength;
        float age = state.age;
        float arcAlpha = state.arcAlpha;
        float trailAlpha = state.trailAlpha;
        if (length <= 0.05f) {
            return;
        }

        // 星芒沿弧带之间错落分布：主星更亮更大，辅星较暗，避免均匀圆点链。
        float[] starPositions = STAR_POSITIONS;
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
        float[] flashPositions = FLASH_POSITIONS;
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
        float[] shardPositions = SHARD_POSITIONS;
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
                                                 VertexConsumer sprites,
                                                 WindArrowRenderState state) {
        float length = state.visibleLength;
        float age = state.age;
        float alpha = state.afterglowAlpha;
        if (length <= 0.1f || alpha <= 0.001f) {
            return;
        }

        int nodeCount = Mth.clamp((int) Math.ceil(length / 0.92f) + 1, 8, 22);
        double[] nodeX = AFTERGLOW_NODE_X;
        double[] nodeY = AFTERGLOW_NODE_Y;
        double[] nodeZ = AFTERGLOW_NODE_Z;

        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            float y = length * t;
            float phase = t * 5.8f - age * 0.17f;
            float x = 0.085f * (float) Math.sin(phase) + 0.032f * (float) Math.sin(phase * 2.8f + 0.7f);
            float z = 0.092f * (float) Math.cos(phase * 1.13f) + 0.027f * (float) Math.sin(phase * 2.1f);
            nodeX[i] = x;
            nodeY[i] = y;
            nodeZ[i] = z;
        }

        // 点线链段不完全接触节点，避免出现一根连续的机械电线。
        for (int i = 0; i < nodeCount - 1; i++) {
            double deltaX = nodeX[i + 1] - nodeX[i];
            double deltaY = nodeY[i + 1] - nodeY[i];
            double deltaZ = nodeZ[i + 1] - nodeZ[i];
            float segmentAlpha = alpha * (i % 3 == 0 ? 0.76f : 0.48f);
            drawTubeSegment(poseStack, glow,
                nodeX[i] + deltaX * 0.18, nodeY[i] + deltaY * 0.18, nodeZ[i] + deltaZ * 0.18,
                nodeX[i + 1] - deltaX * 0.24, nodeY[i + 1] - deltaY * 0.24, nodeZ[i + 1] - deltaZ * 0.24,
                i % 3 == 0 ? 0.0032f : 0.0021f,
                0.18f, 0.48f, 1.0f, segmentAlpha, 4);
        }

        // 节点亮度有节奏差异；少量节点带星芒，其余仅保留小光点。
        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            float pulse = 0.34f + 0.66f * (0.5f + 0.5f * Mth.sin(age * 1.1f + i * 1.73f));
            float size = (i % 4 == 2 ? 0.047f : 0.025f) * (0.8f + 0.2f * pulse);
            float x = (float) nodeX[i];
            float y = (float) nodeY[i];
            float z = (float) nodeZ[i];
            if (i % 3 == 0) {
                WindArrowRenderUtil.drawSprite(poseStack, sprites, x, y, z,
                    size, size * 1.08f, 2, age * 11.0f + i * 23.0f,
                    0.61f, 0.82f, 1.0f, alpha * 0.52f * pulse);
            } else {
                WindArrowRenderUtil.drawSprite(poseStack, sprites, x, y, z,
                    size * 0.52f, size * 0.52f, 0, age * 9.0f + i * 17.0f,
                    0.50f, 0.72f, 1.0f, alpha * 0.42f * pulse);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** 标量化管段：与原 Vec3 实现同序，保留 1.0e-8 短路与 1.0E-4 零向量语义。 */
    private static void drawTubeSegment(PoseStack poseStack, VertexConsumer consumer,
                                        double startX, double startY, double startZ,
                                        double endX, double endY, double endZ,
                                        float radius, float red, float green, float blue, float alpha,
                                        int segments) {
        double dirX = endX - startX;
        double dirY = endY - startY;
        double dirZ = endZ - startZ;
        double dirLenSqr = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (dirLenSqr < 1.0e-8) {
            return;
        }
        double dirLen = Math.sqrt(dirLenSqr);
        if (dirLen >= 1.0E-4) {
            dirX /= dirLen;
            dirY /= dirLen;
            dirZ /= dirLen;
        } else {
            dirX = 0.0;
            dirY = 0.0;
            dirZ = 0.0;
        }
        double refX = Math.abs(dirY) < 0.9 ? 0.0 : 1.0;
        double refY = Math.abs(dirY) < 0.9 ? 1.0 : 0.0;
        double refZ = 0.0;
        double rightX = dirY * refZ - dirZ * refY;
        double rightY = dirZ * refX - dirX * refZ;
        double rightZ = dirX * refY - dirY * refX;
        double rightLen = Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen >= 1.0E-4) {
            rightX /= rightLen;
            rightY /= rightLen;
            rightZ /= rightLen;
        } else {
            rightX = 0.0;
            rightY = 0.0;
            rightZ = 0.0;
        }
        double upX = dirY * rightZ - dirZ * rightY;
        double upY = dirZ * rightX - dirX * rightZ;
        double upZ = dirX * rightY - dirY * rightX;
        double upLen = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen >= 1.0E-4) {
            upX /= upLen;
            upY /= upLen;
            upZ /= upLen;
        } else {
            upX = 0.0;
            upY = 0.0;
            upZ = 0.0;
        }
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double c0 = Math.cos(a0) * radius;
            double s0 = Math.sin(a0) * radius;
            double c1 = Math.cos(a1) * radius;
            double s1 = Math.sin(a1) * radius;
            double o0X = rightX * c0 + upX * s0;
            double o0Y = rightY * c0 + upY * s0;
            double o0Z = rightZ * c0 + upZ * s0;
            double o1X = rightX * c1 + upX * s1;
            double o1Y = rightY * c1 + upY * s1;
            double o1Z = rightZ * c1 + upZ * s1;
            double p0X = startX + o0X;
            double p0Y = startY + o0Y;
            double p0Z = startZ + o0Z;
            double p1X = startX + o1X;
            double p1Y = startY + o1Y;
            double p1Z = startZ + o1Z;
            double p2X = endX + o1X;
            double p2Y = endY + o1Y;
            double p2Z = endZ + o1Z;
            double p3X = endX + o0X;
            double p3Y = endY + o0Y;
            double p3Z = endZ + o0Z;
            float u0 = (float) i / segments;
            float u1 = (float) (i + 1) / segments;
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p0X, (float) p0Y, (float) p0Z, u0, 0,
                red, green, blue, alpha, (float) (o0X / radius), (float) (o0Y / radius), (float) (o0Z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p1X, (float) p1Y, (float) p1Z, u1, 0,
                red, green, blue, alpha, (float) (o1X / radius), (float) (o1Y / radius), (float) (o1Z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p2X, (float) p2Y, (float) p2Z, u1, 1,
                red, green, blue, alpha, (float) (o1X / radius), (float) (o1Y / radius), (float) (o1Z / radius));
            WindArrowRenderUtil.emitVertex(pose, consumer, (float) p3X, (float) p3Y, (float) p3Z, u0, 1,
                red, green, blue, alpha, (float) (o0X / radius), (float) (o0Y / radius), (float) (o0Z / radius));
        }
    }
}

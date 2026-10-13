package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢前锋层：几何楔形箭头 + 专用晶体贴图 + 尖端星芒。
 * 多方向交叉的面让前锋从正视与侧视都可辨识；只调整视觉，不参与碰撞与命中。
 */
public final class WindArrowHeadRenderer {

    private WindArrowHeadRenderer() {
    }

    public static void render(WindArrowRenderState state, WindArrowRenderContext context) {
        PoseStack poseStack = context.poseStack;
        MultiBufferSource bufferSource = context.bufferSource;
        WindArrowRenderUtil.pushTrajectoryPose(poseStack, state);
        // 箭矢前锋：几何楔形保持体积感，专用晶体贴图补足白芯、切面和锐利边缘。
        float tipY = state.visibleLength;
        VertexConsumer glow = bufferSource.getBuffer(WindArrowRenderTypes.GLOW);
        renderGeometryHead(poseStack, glow, tipY, state.headPulse, state.beamAlpha);
        VertexConsumer arrowHead = bufferSource.getBuffer(WindArrowRenderTypes.HEAD);
        renderTextureHead(poseStack, arrowHead, 0.0f, tipY, 0.004f, state.age, state.headTexturePulse, state.beamAlpha);
        // 中心星芒降为较小的尖端高光，避免盖过箭头轮廓。
        VertexConsumer sprites = bufferSource.getBuffer(WindArrowRenderTypes.SPRITES);
        WindArrowRenderUtil.drawSprite(poseStack, sprites, 0.0f, tipY, 0.008f,
            0.034f, 0.047f, 2, state.age * 21.0f,
            0.96f, 0.99f, 1.0f, state.beamAlpha * 0.48f);
        poseStack.popPose();
    }

    /**
     * 绘制箭头前锋的专用晶体图集。贴图尖端与局部 +Y 对齐，沿飞行轴旋转三张面，
     * 叠在几何楔形上形成正视与侧视均可辨识的白色核心、冷蓝切面和锐利轮廓。
     */
    private static void renderTextureHead(PoseStack poseStack, VertexConsumer consumer,
                                          float cx, float tipY, float cz,
                                          float age, float pulse, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }

        float halfWidth = 0.215f * pulse;
        float halfHeight = 0.285f * pulse;
        // 图案顶部的透明边距很小；略向前补偿，让纹理尖端贴合几何箭头顶点。
        float centerY = tipY - halfHeight + 0.018f;

        for (int plane = 0; plane < 3; plane++) {
            poseStack.pushPose();
            poseStack.translate(cx, centerY, cz);
            poseStack.mulPose(Axis.YP.rotationDegrees(plane * 60.0f + age * 2.5f));
            WindArrowRenderUtil.drawSpriteAtOrigin(poseStack, consumer,
                halfWidth, halfHeight, plane == 0 ? 0 : 1,
                0.90f, 0.97f, 1.0f,
                alpha * (plane == 0 ? 0.92f : 0.58f));
            poseStack.popPose();
        }
    }

    /**
     * 风矢尖锋。用三张交叉的渐缩楔形面构成可辨认的箭头，而不是只用星芒
     * 暗示前锋。沿局部 +Y 方向延伸，与该渲染通道的弹道轴保持一致。
     */
    private static void renderGeometryHead(PoseStack poseStack, VertexConsumer consumer,
                                           float tipY, float pulse, float alpha) {
        if (alpha <= 0.001f) return;

        float length = 0.48f * pulse;
        float halfWidth = 0.175f * pulse;
        float baseY = tipY - length;
        float tipAlpha = alpha * 0.98f;
        float wingAlpha = alpha * 0.84f;
        PoseStack.Pose pose = poseStack.last();

        // 正面楔形：亮白中心，青蓝外缘。
        WindArrowRenderUtil.emitVertex(pose, consumer, 0.0f, tipY, 0.0f, 0.5f, 0.0f,
            0.94f, 0.99f, 1.0f, tipAlpha, 0.0f, 0.0f, 1.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, -halfWidth, baseY, 0.0f, 0.0f, 1.0f,
            0.20f, 0.72f, 1.0f, wingAlpha, 0.0f, 0.0f, 1.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, halfWidth, baseY, 0.0f, 1.0f, 1.0f,
            0.20f, 0.72f, 1.0f, wingAlpha, 0.0f, 0.0f, 1.0f);

        // 侧向交叉楔形，避免从俯视或侧视时箭头完全变成一条线。
        WindArrowRenderUtil.emitVertex(pose, consumer, 0.0f, tipY, 0.0f, 0.5f, 0.0f,
            0.80f, 0.94f, 1.0f, tipAlpha * 0.92f, 1.0f, 0.0f, 0.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, 0.0f, baseY, -halfWidth * 0.72f, 0.0f, 1.0f,
            0.12f, 0.52f, 1.0f, wingAlpha * 0.82f, 1.0f, 0.0f, 0.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, 0.0f, baseY, halfWidth * 0.72f, 1.0f, 1.0f,
            0.12f, 0.52f, 1.0f, wingAlpha * 0.82f, 1.0f, 0.0f, 0.0f);

        // 短白芯将箭头尖端与蓝色主束视觉连接起来。
        float coreWidth = halfWidth * 0.19f;
        float coreBase = tipY - length * 0.72f;
        WindArrowRenderUtil.emitVertex(pose, consumer, 0.0f, tipY + 0.012f, 0.001f, 0.5f, 0.0f,
            1.0f, 1.0f, 1.0f, alpha, 0.0f, 0.0f, 1.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, -coreWidth, coreBase, 0.001f, 0.0f, 1.0f,
            0.72f, 0.91f, 1.0f, alpha * 0.92f, 0.0f, 0.0f, 1.0f);
        WindArrowRenderUtil.emitVertex(pose, consumer, coreWidth, coreBase, 0.001f, 1.0f, 1.0f,
            0.72f, 0.91f, 1.0f, alpha * 0.92f, 0.0f, 0.0f, 1.0f);
    }
}

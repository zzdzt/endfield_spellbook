package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 风矢共享底层绘制原语：顶点发射、atlas 星芒贴图与通用四边形。
 * 只封装顶点写入与 pose 推拉，不持有任何 RenderType 或视觉参数。
 */
public final class WindArrowRenderUtil {

    private WindArrowRenderUtil() {
    }

    /** 进入弹道局部坐标系：抬升实体包围盒半高并按弹道 yaw/pitch 旋转，配对 popPose 使用。 */
    static void pushTrajectoryPose(PoseStack poseStack, WindArrowRenderState state) {
        poseStack.pushPose();
        poseStack.translate(0, state.bodyHalfHeight, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yawDegrees - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitchDegrees - 90.0F));
    }

    /** 沿弹道轴放置一张横向旋转的星芒/碎片贴图。 */
    static void drawSprite(PoseStack poseStack, VertexConsumer consumer,
                           float cx, float cy, float cz,
                           float halfWidth, float halfHeight, int sprite,
                           float rotationDegrees, float red, float green,
                           float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        drawSpriteAtOrigin(poseStack, consumer, halfWidth, halfHeight, sprite,
            red, green, blue, alpha);
        poseStack.popPose();
    }

    /**
     * 绘制 wind_arrow_star_glint.png 的单格 atlas。
     * atlas 为 4 列、每列占 0.25 的 U 区间；透明边缘依赖线性过滤柔化。
     */
    static void drawSpriteAtOrigin(PoseStack poseStack, VertexConsumer consumer,
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

    /** 通用正方形四边形：UV 全覆盖，size 与颜色由调用方给定，无通道特有逻辑。 */
    static void drawQuad(PoseStack poseStack, VertexConsumer consumer,
                         float size, float red, float green, float blue, float alpha) {
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

    static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer,
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

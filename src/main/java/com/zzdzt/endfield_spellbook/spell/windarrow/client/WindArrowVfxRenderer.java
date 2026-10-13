package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢穿林视觉编排门面：每帧构建 {@link WindArrowRenderState}，再把轨迹、
 * 前锋与命中三个渲染通道按固定顺序装配进 pose 树。只控制流程，不绘制细节。
 *
 * <p>RenderType 绑定见 {@link WindArrowRenderTypes}，共享绘制原语见
 * {@link WindArrowRenderUtil}。不负责射线命中或伤害结算。
 */
public final class WindArrowVfxRenderer {

    private WindArrowVfxRenderer() {
    }

    public static void render(LizhiYanBlastVisualEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        if (entity.distance <= 0.001f) {
            return;
        }

        WindArrowRenderState state = WindArrowRenderState.create(entity, partialTicks);
        if (state.isExpired()) {
            return;
        }

        // 关键：先在世界轴坐标中生成 camera-facing ribbon，避免经过 yaw/pitch 旋转后变成侧边线。
        WindArrowTrailRenderer.renderMainRibbon(state, poseStack, bufferSource);

        // 弧带、星点、前锋与命中围绕弹道局部坐标系展开。
        poseStack.pushPose();
        poseStack.translate(0, entity.getBoundingBox().getYsize() * 0.5f, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot() - 90.0F));

        WindArrowTrailRenderer.renderSwirlTrail(state, poseStack, bufferSource);
        WindArrowTrailRenderer.renderStarGlint(state, poseStack, bufferSource);
        WindArrowTrailRenderer.renderAfterglow(state, poseStack, bufferSource);
        WindArrowHeadRenderer.renderHead(state, poseStack, bufferSource);
        WindArrowImpactRenderer.renderImpact(state, entity, poseStack, bufferSource);

        poseStack.popPose();
    }
}

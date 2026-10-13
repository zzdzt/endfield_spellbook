package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢命中层：普通命中爆闪与终结放射爆闪，全部为贴图三平面交叉绘制。
 * 爆闪在箭矢前锋抵达弹道终点后（state.impactAge ≥ 0）展开。
 */
public final class WindArrowImpactRenderer {

    private WindArrowImpactRenderer() {
    }

    public static void render(WindArrowRenderState state, WindArrowRenderContext context) {
        if (state.impactAge < 0f) {
            return;
        }
        PoseStack poseStack = context.poseStack;
        MultiBufferSource bufferSource = context.bufferSource;

        WindArrowRenderUtil.pushTrajectoryPose(poseStack, state);
        if (state.visualStyle == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_HIT) {
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WindArrowRenderTypes.IMPACT),
                state, false);
        } else if (state.visualStyle == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_FINAL_HIT) {
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WindArrowRenderTypes.IMPACT_FINAL),
                state, true);
        }
        poseStack.popPose();
    }

    /**
     * 贴图版命中爆闪：三张交叉面避免从侧面观看时星芒消失；外层蓝白、内层近白。
     */
    private static void drawArrowImpactTexture(PoseStack poseStack, VertexConsumer consumer,
                                               WindArrowRenderState state, boolean finalBurst) {
        float cy = state.visibleLength;
        float impactAge = state.impactAge;
        // 进度与衰减曲线来自 RenderState（普通 3s / 终结 5.5s）；eased 与尺寸属于几何映射，留在通道内。
        float progress = finalBurst ? state.impactProgressFinal : state.impactProgressNormal;
        float fade = finalBurst ? state.impactFadeFinal : state.impactFadeNormal;
        float eased = progress * progress * (3.0f - 2.0f * progress);
        // 普通命中之前尺寸偏小，纹理的大部分透明边距又进一步削弱了视觉面积。
        // 提高首帧尺寸与白芯强度，让普通命中一开始就可读；终结爆闪仍维持更大尺度。
        float size = finalBurst ? 0.30f + eased * 1.18f : 0.28f + eased * 0.72f;
        float outerAlpha = fade * (finalBurst ? 0.48f : 0.56f);
        float coreAlpha = fade * (finalBurst ? 0.82f : 0.94f);

        for (int plane = 0; plane < 3; plane++) {
            poseStack.pushPose();
            poseStack.translate(0, cy, 0);
            if (plane == 1) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            } else if (plane == 2) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            WindArrowRenderUtil.drawQuad(poseStack, consumer, size * 1.15f,
                0.40f, 0.66f, 1.0f, outerAlpha);
            WindArrowRenderUtil.drawQuad(poseStack, consumer, size * 0.66f,
                0.88f, 0.97f, 1.0f, coreAlpha);
            poseStack.popPose();
        }
    }
}

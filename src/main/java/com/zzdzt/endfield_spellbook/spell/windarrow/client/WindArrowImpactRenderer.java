package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * 风矢命中层：普通命中爆闪与终结放射爆闪，全部为贴图三平面交叉绘制。
 * 爆闪在箭矢前锋（age ≥ 3.0）抵达弹道终点后展开。
 */
public final class WindArrowImpactRenderer {

    /** 前锋抵达弹道终点的时刻，命中爆闪由此开始展开。 */
    private static final float IMPACT_START_AGE = 3.0f;

    private WindArrowImpactRenderer() {
    }

    public static void render(WindArrowRenderState state, WindArrowRenderContext context) {
        if (state.age < IMPACT_START_AGE) {
            return;
        }
        PoseStack poseStack = context.poseStack;
        MultiBufferSource bufferSource = context.bufferSource;

        WindArrowRenderUtil.pushTrajectoryPose(poseStack, state);
        if (state.visualStyle == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_HIT) {
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WindArrowRenderTypes.IMPACT),
                0, state.visibleLength, 0, state.age - IMPACT_START_AGE, false);
        } else if (state.visualStyle == LizhiYanBlastVisualEntity.STYLE_WIND_ARROW_FINAL_HIT) {
            drawArrowImpactTexture(poseStack, bufferSource.getBuffer(WindArrowRenderTypes.IMPACT_FINAL),
                0, state.visibleLength, 0, state.age - IMPACT_START_AGE, true);
        }
        poseStack.popPose();
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
        // 普通命中之前尺寸偏小，纹理的大部分透明边距又进一步削弱了视觉面积。
        // 提高首帧尺寸与白芯强度，让普通命中一开始就可读；终结爆闪仍维持更大尺度。
        float size = finalBurst ? 0.30f + eased * 1.18f : 0.28f + eased * 0.72f;
        float outerAlpha = fade * (finalBurst ? 0.48f : 0.56f);
        float coreAlpha = fade * (finalBurst ? 0.82f : 0.94f);

        for (int plane = 0; plane < 3; plane++) {
            poseStack.pushPose();
            poseStack.translate(cx, cy, cz);
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

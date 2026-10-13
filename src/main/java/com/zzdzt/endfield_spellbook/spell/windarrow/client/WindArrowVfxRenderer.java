package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢穿林视觉编排门面：每帧构建 {@link WindArrowRenderState}，再依次调度
 * 轨迹、前锋与命中三个渲染通道。只控制流程与生命周期，不做任何绘制与实体状态读取。
 *
 * <p>RenderType 绑定见 {@link WindArrowRenderTypes}，共享绘制原语见
 * {@link WindArrowRenderUtil}。
 */
public final class WindArrowVfxRenderer {

    private WindArrowVfxRenderer() {
    }

    public static void render(LizhiYanBlastVisualEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        WindArrowRenderState state = WindArrowRenderState.create(entity, partialTicks);
        if (!state.visible() || state.isExpired()) {
            return;
        }

        WindArrowTrailRenderer.render(state, poseStack, bufferSource);
        WindArrowHeadRenderer.render(state, poseStack, bufferSource);
        WindArrowImpactRenderer.render(state, poseStack, bufferSource);
    }
}

package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢穿林视觉编排门面：每帧组装 {@link WindArrowRenderContext} 与
 * {@link WindArrowRenderState}，再依次调度轨迹、前锋与命中三个渲染通道。
 * 只控制流程与生命周期，不做任何绘制。
 *
 * <p>RenderType 绑定见 {@link WindArrowRenderTypes}，共享绘制原语见
 * {@link WindArrowRenderUtil}。
 */
public final class WindArrowVfxRenderer {

    private WindArrowVfxRenderer() {
    }

    public static void render(LizhiYanBlastVisualEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        WindArrowRenderContext context =
            new WindArrowRenderContext(poseStack, bufferSource, camera, partialTicks);
        WindArrowRenderState state = WindArrowRenderState.create(entity, context.partialTicks);
        if (!state.visible() || state.isExpired()) {
            return;
        }

        WindArrowTrailRenderer.render(state, context);
        WindArrowHeadRenderer.render(state, context);
        WindArrowImpactRenderer.render(state, context);
    }
}

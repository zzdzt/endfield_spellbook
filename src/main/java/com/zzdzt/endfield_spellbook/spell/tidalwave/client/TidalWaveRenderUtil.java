package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 踏潮卷浪绘制原语（≈WindArrowRenderUtil）：包私有，仅顶点级工具，不持状态。
 */
final class TidalWaveRenderUtil {

    private TidalWaveRenderUtil() {
    }

    /** 全亮无 overlay 顶点（法线固定向上——本技能几何均为近水平带）。 */
    static void emitVertex(VertexConsumer consumer, PoseStack.Pose pose,
                           float x, float y, float z, float u, float v,
                           float r, float g, float b, float a) {
        consumer.vertex(pose.pose(), x, y, z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(pose.normal(), 0f, 1f, 0f)
            .endVertex();
    }
}

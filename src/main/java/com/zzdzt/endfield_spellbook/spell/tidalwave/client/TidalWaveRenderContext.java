package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 踏潮卷浪渲染上下文（≈WindArrowRenderContext，P4-A 对齐）：
 * 每帧构建一次，向各渲染通道传递绘制环境。无 camera——本技能无 billboard 需求。
 */
public final class TidalWaveRenderContext {

    public final PoseStack poseStack;
    public final MultiBufferSource bufferSource;
    public final float partialTicks;

    public TidalWaveRenderContext(PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks) {
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.partialTicks = partialTicks;
    }
}

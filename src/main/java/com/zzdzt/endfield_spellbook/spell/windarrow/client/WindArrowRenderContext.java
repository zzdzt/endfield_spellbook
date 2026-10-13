package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 风矢视觉的单帧渲染环境：pose 树、缓冲源、相机与部分tick。
 * 由 {@link WindArrowVfxRenderer} 每帧构建一次，替代 renderer 间逐层传递的松散参数。
 */
public final class WindArrowRenderContext {
    public final PoseStack poseStack;
    public final MultiBufferSource bufferSource;
    public final Camera camera;
    public final float partialTicks;

    public WindArrowRenderContext(PoseStack poseStack, MultiBufferSource bufferSource,
                                  Camera camera, float partialTicks) {
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.camera = camera;
        this.partialTicks = partialTicks;
    }
}

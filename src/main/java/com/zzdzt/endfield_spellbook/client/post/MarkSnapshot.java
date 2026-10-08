package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkEntity;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 实体 pass 冻结的破晦阵渲染参数快照。
 *
 * 实体 pass 只收集不绘制；AFTER_LEVEL 阶段按快照重绘到自研 FBO。
 * f / alpha（含 fade 折算）/ dist 在实体 pass 算好冻结，保证与直接绘制
 * 的视觉参数完全一致（同帧同 partialTick，无跨帧问题）。
 */
public record MarkSnapshot(GloompurgeMarkEntity entity, float f, float alpha, double dist, int shape) implements PostFxSnapshot {

    @Override
    public void drawInto(PoseStack poseStack, MultiBufferSource.BufferSource buffers, boolean depthReady) {
        var set = PostRenderTypes.set(depthReady);
        // renderer 实例承载 fadeTick 等实例状态，从 dispatcher 反查
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof GloompurgeMarkRenderer gmr) {
            gmr.drawForPipeline(entity, f, alpha, dist, shape, poseStack, buffers, set);
        }
    }

    @Override
    public void endBatches(MultiBufferSource.BufferSource buffers, boolean depthReady) {
        var set = PostRenderTypes.set(depthReady);
        buffers.endBatch(set.mark());
        buffers.endBatch(set.ground());
        buffers.endBatch(set.mist());
        buffers.endBatch(set.glow());
    }
}

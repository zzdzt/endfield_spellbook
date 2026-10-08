package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileEntity;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 实体 pass 冻结的液氮炮弹渲染参数快照。
 *
 * 实体 pass 只收集不绘制；AFTER_LEVEL 阶段按快照重绘到自研 FBO，
 * 与破晦阵 MarkSnapshot 共用一条管线与 bloom。
 */
public record LncSnapshot(LncProjectileEntity entity, float f) implements PostFxSnapshot {

    @Override
    public void drawInto(PoseStack poseStack, MultiBufferSource.BufferSource buffers, boolean depthReady) {
        var set = PostRenderTypes.set(depthReady);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof LncProjectileRenderer lncRenderer) {
            lncRenderer.drawForPipeline(entity, f, poseStack, buffers, set.glow());
        }
    }

    @Override
    public void endBatches(MultiBufferSource.BufferSource buffers, boolean depthReady) {
        buffers.endBatch(PostRenderTypes.set(depthReady).glow());
    }
}

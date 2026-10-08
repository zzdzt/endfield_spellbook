package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.FlameRingEntity;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.FlameRingRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 焚灭火环实体 pass 的渲染快照。
 *
 * <p>火环在主 RT 的实体阶段只采集时间/实体引用；AFTER_LEVEL 再以独立 CA1/2/3
 * 重绘，并经过 FlameRingPass 的屏幕空间扰动后合入 CA0，最后统一 bloom。
 * 扭曲的强度/贴图采样属于火环自身知识，封装在 {@link #runScreenSpacePass()}。</p>
 */
public record FlameRingSnapshot(FlameRingEntity entity, float f) implements PostFxSnapshot {

    @Override
    public void drawInto(PoseStack poseStack, MultiBufferSource.BufferSource buffers, boolean depthReady) {
        var flameSet = PostRenderTypes.setFlameRing(depthReady);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof FlameRingRenderer frRenderer) {
            frRenderer.drawForPipeline(entity, f, poseStack, buffers, flameSet);
        }
    }

    @Override
    public void endBatches(MultiBufferSource.BufferSource buffers, boolean depthReady) {
        // 逐批次 endBatch：确保每层 setup（绑各自 CA）→ 绘制 → teardown 完整执行
        var flameSet = PostRenderTypes.setFlameRing(depthReady);
        buffers.endBatch(flameSet.outer());
        buffers.endBatch(flameSet.body());
        buffers.endBatch(flameSet.core());
    }

    @Override
    public boolean hasScreenSpacePass() {
        return true;
    }

    @Override
    public void runScreenSpacePass() {
        var fx = PipelinePost.fxBuffer();
        PipelinePost.flameRingPass().render(
            fx.colorTexture(1),
            fx.colorTexture(2),
            fx.colorTexture(3),
            fx,
            f,
            2.75f);
    }
}

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
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof FlameRingRenderer frRenderer) {
            // 单一分派（P1.3）：Pass 可用 → 三材质写 CA1/2/3；不可用 → 三层直写公共 CA0
            //（FinalPass 只合成 CA0，跳过扭曲 Pass 还画 CA1-3 的话环会不可见）。
            var set = PipelinePost.flameRingPassUsable()
                ? PostRenderTypes.setFlameRing(depthReady)
                : PostRenderTypes.setFlameRingFallback(depthReady);
            frRenderer.drawForPipeline(entity, f, poseStack, buffers, set);
        }
    }

    @Override
    public void endBatches(MultiBufferSource.BufferSource buffers, boolean depthReady) {
        // 与 drawInto 同帧同判定（可用性标志只在 init/step④ 翻转，③ 阶段恒定），
        // 保证结束的正是本次实际使用的批次
        var set = PipelinePost.flameRingPassUsable()
            ? PostRenderTypes.setFlameRing(depthReady)
            : PostRenderTypes.setFlameRingFallback(depthReady);
        // 逐批次 endBatch：确保每层 setup（绑各自目标附件）→ 绘制 → teardown 完整执行
        buffers.endBatch(set.outer());
        buffers.endBatch(set.body());
        buffers.endBatch(set.core());
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

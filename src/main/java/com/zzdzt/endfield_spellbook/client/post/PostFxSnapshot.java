package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

/**
 * 后处理管线的特效快照统一接口（技术债泛化，见 docs/技术债-后处理快照队列泛化.md）。
 *
 * <p>实体 pass 冻结渲染参数成实现 record；AFTER_LEVEL 阶段管线统一消费：
 * translate 由管线完成，绘制与批次归属由快照自报——新增一种管线特效 =
 * 新增接口实现 + renderer 绘制方法 + enqueue 调用，PipelinePost 零修改。</p>
 *
 * <p>实现必须遵守管线四件事：降级链（异常→failed→回退直绘）、零开销短路
 * （队列空整帧跳过）、AFTER_LEVEL 姿态矩阵坑（管线自建相机旋转，实现只做
 * 实体局部绘制）、逐批次 endBatch（跨同类型快照共享批次，每帧每类型只收尾一次）。</p>
 */
public interface PostFxSnapshot {

    /** 快照对应的实体（管线用它计算 translate：entity - camPos）。record 组件同名即隐式满足。 */
    Entity entity();

    /**
     * 重绘到自研 FBO。调用时 poseStack 已平移到实体位置（相机旋转已由管线自建），
     * 实现内部从 dispatcher 反查 renderer 实例承载 fadeTick 等实例状态。
     */
    void drawInto(PoseStack poseStack, MultiBufferSource.BufferSource buffers, boolean depthReady);

    /** 批次收尾：本类型全部快照绘制完毕后调用（每帧每实现类型恰好一次）。 */
    void endBatches(MultiBufferSource.BufferSource buffers, boolean depthReady);

    /** 绘制完成后是否需要附加的 screen-space 处理（如火环三材质扭曲）。 */
    default boolean hasScreenSpacePass() {
        return false;
    }

    /**
     * 执行 screen-space 附加处理。管线在全部快照绘制完后调用，
     * 每帧只执行第一个声明者的（与重构前"取 get(0) 时间"语义一致）。
     * 默认空实现：被 {@link #hasScreenSpacePass()} 门控，无附加段的类型无需覆写。
     */
    default void runScreenSpacePass() {
    }
}

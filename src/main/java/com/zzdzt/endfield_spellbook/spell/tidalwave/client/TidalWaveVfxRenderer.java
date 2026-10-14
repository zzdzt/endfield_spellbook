package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WaterTornadoEntity;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WhirlpoolEntity;
import net.minecraft.util.Mth;

/**
 * 踏潮卷浪 VFX 编排门面（≈WindArrowVfxRenderer）：
 * 构建 RenderState → 可见性闸 → 调通道方法。不持状态、不算时间曲线。
 *
 * <p>视觉形态（设计文档 §14）：
 * <ul>
 *   <li>涡流：贴地双层反向旋转水环 + 中心小漩涡锥 + 寒气粒子（实体 clientTick）</li>
 *   <li>水龙卷：三层螺旋水柱（半径随高度收缩、滚动上升）+ 底部溅射环 + 冰蓝高光白芯</li>
 * </ul>
 * 纯几何 ribbon + 项目既有纯白贴图 + 顶点色；不碰 PipelinePost。
 * 热路径零分配：全部标量局部变量，无 new Vec3。
 */
public final class TidalWaveVfxRenderer {

    // ===== CRYO 三层色板（外晕 / 主体 / 高光白芯） =====
    private static final float[] CRYO_OUTER = {0.42f, 0.68f, 0.95f};
    private static final float[] CRYO_BODY = {0.55f, 0.80f, 1.00f};
    private static final float[] CRYO_CORE = {0.88f, 0.95f, 1.00f};

    private TidalWaveVfxRenderer() {
    }

    // ============================================================
    // 涡流
    // ============================================================

    public static void renderWhirlpool(WhirlpoolEntity entity, TidalWaveRenderContext context) {
        TidalWaveRenderState state = TidalWaveRenderState.createForWhirlpool(entity, context.partialTicks);
        if (!state.visible()) {
            return;
        }
        PoseStack.Pose pose = context.poseStack.last();
        VertexConsumer ring = context.bufferSource.getBuffer(TidalWaveRenderTypes.ring());
        float fade = state.whirlpoolFade;

        // 外环（正转）
        drawRingStrip(ring, pose, 0.90f, 0.18f, 0.06f, state.whirlpoolSpin, 0.06f,
            CRYO_OUTER, 0.50f * fade, 28, 2f);
        // 内环（反转）
        drawRingStrip(ring, pose, 0.55f, 0.12f, 0.08f, -state.whirlpoolSpin * 1.3f, 0.09f,
            CRYO_BODY, 0.60f * fade, 24, 2f);
        // 中心漩涡锥
        drawSpiralStrip(ring, pose, 0.30f, 0.06f, 0.45f, 0.08f, state.whirlpoolSpin * 2f, 0.05f,
            CRYO_CORE, 0.55f * fade, 16, 1f);
    }

    // ============================================================
    // 水龙卷
    // ============================================================

    public static void renderWaterTornado(WaterTornadoEntity entity, TidalWaveRenderContext context) {
        TidalWaveRenderState state = TidalWaveRenderState.createForTornado(entity, context.partialTicks);
        if (!state.visible()) {
            return;
        }
        PoseStack.Pose pose = context.poseStack.last();
        float fade = state.tornadoFade;
        float height = 3.0f * state.riseProgress;

        VertexConsumer ring = context.bufferSource.getBuffer(TidalWaveRenderTypes.ring());
        // 底部溅射环
        drawRingStrip(ring, pose, 1.25f, 0.25f, 0.05f, state.twistOuter * 0.3f, 0.05f,
            CRYO_OUTER, 0.35f * fade * state.riseProgress, 32, 2.5f);
        // 外层螺旋（正转）
        drawSpiralStrip(ring, pose, 1.20f, 0.40f, height, 0.16f, state.twistOuter, 0f,
            CRYO_OUTER, 0.30f * fade, 40, 3f);
        // 主体螺旋（反转）
        drawSpiralStrip(ring, pose, 0.85f, 0.28f, height, 0.12f, state.twistInner, 0f,
            CRYO_BODY, 0.50f * fade, 36, 3f);
        // 冰蓝高光白芯（加法）
        VertexConsumer glow = context.bufferSource.getBuffer(TidalWaveRenderTypes.glow());
        drawSpiralStrip(glow, pose, 0.50f, 0.16f, height, 0.07f, state.twistOuter * 1.5f, 0f,
            CRYO_CORE, 0.80f * fade, 28, 2f);
    }

    // ============================================================
    // 几何通道（B 类：含顶点序几何计算，留在 Renderer 层）
    // ============================================================

    /** 水平环形 ribbon：半径 wobble 抖动 + 整体旋转，径向宽度。 */
    private static void drawRingStrip(VertexConsumer consumer, PoseStack.Pose pose,
                                      float radius, float width, float wobbleAmp, float spinDeg,
                                      float y, float[] color, float alpha, int segments, float uTile) {
        float spin = (float) Math.toRadians(spinDeg);
        float hw = width * 0.5f;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments, t1 = (float) (i + 1) / segments;
            float a0 = spin + t0 * Mth.TWO_PI, a1 = spin + t1 * Mth.TWO_PI;
            float w0 = 1 + wobbleAmp * Mth.sin(i * 1.7f + spin * 0.9f);
            float w1 = 1 + wobbleAmp * Mth.sin((i + 1) * 1.7f + spin * 0.9f);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float r0i = (radius - hw) * w0, r0o = (radius + hw) * w0;
            float r1i = (radius - hw) * w1, r1o = (radius + hw) * w1;
            TidalWaveRenderUtil.emitVertex(consumer, pose, c0 * r0i, y, s0 * r0i, t0 * uTile, 0,
                color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c0 * r0o, y, s0 * r0o, t0 * uTile, 1,
                color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c1 * r1o, y, s1 * r1o, t1 * uTile, 1,
                color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c1 * r1i, y, s1 * r1i, t1 * uTile, 0,
                color[0], color[1], color[2], alpha);
        }
    }

    /** 竖直螺旋 ribbon：半径底→顶收缩，1.5 圈，径向宽度。 */
    private static void drawSpiralStrip(VertexConsumer consumer, PoseStack.Pose pose,
                                        float radiusBottom, float radiusTop, float height, float width,
                                        float twistDeg, float y0, float[] color, float alpha,
                                        int segments, float uTile) {
        float twist = (float) Math.toRadians(twistDeg);
        float hw = width * 0.5f;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments, t1 = (float) (i + 1) / segments;
            float a0 = twist + t0 * Mth.TWO_PI * 1.5f, a1 = twist + t1 * Mth.TWO_PI * 1.5f;
            float rad0 = Mth.lerp(t0, radiusBottom, radiusTop);
            float rad1 = Mth.lerp(t1, radiusBottom, radiusTop);
            float yy0 = y0 + t0 * height, yy1 = y0 + t1 * height;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c0 * (rad0 - hw), yy0, s0 * (rad0 - hw),
                t0 * uTile, 0, color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c0 * (rad0 + hw), yy0, s0 * (rad0 + hw),
                t0 * uTile, 1, color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c1 * (rad1 + hw), yy1, s1 * (rad1 + hw),
                t1 * uTile, 1, color[0], color[1], color[2], alpha);
            TidalWaveRenderUtil.emitVertex(consumer, pose, c1 * (rad1 - hw), yy1, s1 * (rad1 - hw),
                t1 * uTile, 0, color[0], color[1], color[2], alpha);
        }
    }
}

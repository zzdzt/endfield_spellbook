package com.zzdzt.endfield_spellbook.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.client.fx.FxGeometry;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 水墨雷瀑渲染器（移植自 ArcaneMag 惊霆诀定版 V2 的瀑布雷柱）：
 *
 * <p>中央大瀑布 = 暗芯柱（白芯 + 青壳保体积，tier≥3 夹朱红芯）
 * + 多股垂落丝线（顶亮底散、横向摆尾随高度增大、行波相位随时间下行 = 流动感）；
 * 副瀑布 2+tier 条散布 spread 内。
 *
 * <p>本地时序：t0~1 打击帧过冲（×1.35 宽）→ 呼吸 → t7~10 收束。
 * 确定性哈希 seed = entity id（无新增同步字段，形状稳定）。
 */
public class InkWaterfallRenderer extends EntityRenderer<InkWaterfallEntity> {

    private static final ResourceLocation WHITE_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    /** 加算层（顶点色驱动，仅写颜色，进粒子缓冲吃 bloom）。 */
    private static final RenderType RT_ADD =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("ink_waterfall_add", WHITE_TEX);

    // 色板（AM 定版瀑布三色 + 朱红芯）
    private static final float[] COL_WHITE = {0.85f, 0.96f, 0.94f};
    private static final float[] COL_CYAN = {0.35f, 0.72f, 0.68f};
    private static final float[] COL_RED = {0.86f, 0.24f, 0.20f};

    // 本地时序（与实体 PILLAR_LIFETIME=10 对齐）
    private static final float SHRINK_START = 7f;
    private static final float SHRINK_LEN = 3f;

    public InkWaterfallRenderer(Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(InkWaterfallEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        int seed = entity.getId();
        int tier = entity.getTier();
        float length = entity.getHeight();
        float spread = entity.getSpread();
        float f = entity.tickCount + partialTick;

        // 打击帧闪现（1t 过冲）→ 持续段呼吸 → t7~10 收束
        float burst = f < 1f ? 1.35f : 1.0f;
        float breathe = 1f + 0.10f * Mth.sin(Mth.TWO_PI * f / 8f);
        float shrinkK = Mth.clamp((f - SHRINK_START) / SHRINK_LEN, 0f, 1f);
        float widthMul = burst * breathe * (1f - 0.8f * shrinkK);
        float alphaMul = (1f - shrinkK) * 0.95f;
        if (alphaMul <= 0.02f) {
            return;
        }

        VertexConsumer add = bufferSource.getBuffer(RT_ADD);
        int subCount = Math.min(6, 2 + tier);
        float yBase = 0.05f;

        FxGeometry.FloatCurve vFade = p -> {
            float a = 1f;
            if (p < 0.02f) a = p / 0.02f;                     // 底端微渐入
            else if (p > 0.75f) a = 1f - (p - 0.75f) / 0.25f; // 顶部 1/4 渐隐
            return Mth.clamp(a, 0f, 1f);
        };

        // —— 中央大瀑布：暗芯柱（体积）+ 红芯（tier≥3）+ 垂落丝
        boolean hasRed = tier >= 3;
        pillarBeam(poseStack, add, 0f, 0f, yBase, length, 0.30f * widthMul, alphaMul * (hasRed ? 0.25f : 0.40f), COL_WHITE, vFade);
        pillarBeam(poseStack, add, 0f, 0f, yBase, length, 0.50f * widthMul, alphaMul * 0.25f, COL_CYAN, vFade);
        if (hasRed) {
            // 满充红芯加宽提亮，压过白芯 bloom
            pillarBeam(poseStack, add, 0f, 0f, yBase, length, 0.22f * widthMul, alphaMul * 1.0f, COL_RED, vFade);
        }
        for (int i = 0; i < 6; i++) {
            float az = hash01(seed, 600 + i * 83) * Mth.TWO_PI;
            float rr = (0.15f + 0.45f * hash01(seed, 610 + i * 47)) * widthMul;
            float[] col = (i % 2 == 0) ? COL_WHITE : COL_CYAN;
            float shimmer = 0.75f + 0.25f * Mth.sin(f * 0.3f + i * 2.1f);
            waterfallStrand(poseStack, add, 0f, 0f, az, rr, yBase, length,
                (0.07f + 0.07f * hash01(seed, 620 + i * 29)) * widthMul,
                alphaMul * shimmer * (i % 2 == 0 ? 0.85f : 0.6f),
                col, hash01(seed, 630 + i) * Mth.TWO_PI,
                0.18f + 0.12f * hash01(seed, 640 + i), f);
        }

        // —— 副瀑布 ×subCount：小尺寸垂落
        for (int i = 1; i <= subCount; i++) {
            float az = hash01(seed, 300 + i * 77) * Mth.TWO_PI;
            float dist = Mth.sqrt(hash01(seed, 310 + i * 41)) * spread;
            float px = Mth.cos(az) * dist;
            float pz = Mth.sin(az) * dist;
            float hMul = 0.88f + 0.12f * hash01(seed, 320 + i * 53);

            pillarBeam(poseStack, add, px, pz, yBase, length * hMul, 0.18f * widthMul, alphaMul * 0.5f, COL_WHITE, vFade);
            for (int s = 0; s < 2; s++) {
                float saz = hash01(seed, 700 + i * 31 + s * 13) * Mth.TWO_PI;
                float rr = (0.10f + 0.30f * hash01(seed, 710 + i * 19 + s)) * widthMul;
                float[] col = (s % 2 == 0) ? COL_WHITE : COL_CYAN;
                float shimmer = 0.75f + 0.25f * Mth.sin(f * 0.3f + i * 1.7f + s * 2.3f);
                waterfallStrand(poseStack, add, px, pz, saz, rr, yBase, length * hMul,
                    (0.05f + 0.05f * hash01(seed, 720 + i * 7 + s)) * widthMul,
                    alphaMul * shimmer * 0.7f,
                    col, hash01(seed, 730 + i * 3 + s) * Mth.TWO_PI,
                    0.2f + 0.15f * hash01(seed, 740 + i + s), f);
            }
        }
    }

    /** 单根光柱：2 点 polylineRibbon（底→顶，正对相机，宽度方向水平）。 */
    private static void pillarBeam(PoseStack poseStack, VertexConsumer add,
                                   float px, float pz, float yBase, float topY, float width, float alpha,
                                   float[] col, FxGeometry.FloatCurve vFade) {
        if (alpha <= 0.02f || width <= 0.005f) return;
        FxGeometry.polylineRibbon(poseStack, add,
            List.of(new Vector3f(px, yBase, pz), new Vector3f(px, topY, pz)),
            p -> width, vFade,
            alpha, col[0], col[1], col[2], 0.4f, 0.6f);
    }

    /**
     * 单股垂落丝线（瀑布流）：顶亮底散、横向摆尾随高度增大、行波相位随时间下行。
     *
     * @param rr   丝线距柱轴的半径
     * @param az   丝线方位角
     * @param flow 下行流速——相位随 f 递减产生「水往下流」
     */
    private static void waterfallStrand(PoseStack poseStack, VertexConsumer add,
                                        float px, float pz, float az, float rr, float yBase, float topY,
                                        float width, float alpha, float[] col,
                                        float phase, float flow, float f) {
        if (alpha <= 0.02f || width <= 0.005f) return;
        // 切向（摆动方向）与径向基
        float tx = -Mth.sin(az), tz = Mth.cos(az);
        float bx = px + Mth.cos(az) * rr;
        float bz = pz + Mth.sin(az) * rr;

        List<Vector3f> path = new ArrayList<>(8);
        for (int k = 0; k < 8; k++) {
            float p = (float) k / 7f;                 // 0=顶 1=底
            float y = topY + (yBase - topY) * p;
            // 摆动幅度沿程增大（顶部贴柱、底部甩尾），行波相位随时间下行
            float sway = Mth.sin(phase + p * Mth.TWO_PI * 1.5f - flow * f) * 0.35f * p;
            // 底部轻微外散（水落散开）
            float spreadK = rr * 0.5f * p;
            path.add(new Vector3f(
                bx + tx * sway + Mth.cos(az) * spreadK,
                y,
                bz + tz * sway + Mth.sin(az) * spreadK));
        }
        // 顶部亮 → 底部散淡（瀑布消散）
        FxGeometry.FloatCurve fade = p -> 1f - 0.65f * p;
        FxGeometry.polylineRibbon(poseStack, add, path,
            p -> width * (0.7f + 0.3f * p), fade,
            alpha, col[0], col[1], col[2], 0.4f, 0.6f);
    }

    /** 确定性哈希（AM 同款整数雪崩），[0,1)。 */
    private static float hash01(int seed, int salt) {
        int h = seed * 0x27D4EB2D + salt * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    @Override
    public boolean shouldRender(InkWaterfallEntity entity, net.minecraft.client.renderer.culling.Frustum camera,
                                double camX, double camY, double camZ) {
        float h = entity.getHeight();
        return camera.isVisible(new AABB(
            entity.getX() - 3, entity.getY() - 0.5, entity.getZ() - 3,
            entity.getX() + 3, entity.getY() + h + 0.5, entity.getZ() + 3));
    }

    @Override
    public ResourceLocation getTextureLocation(InkWaterfallEntity entity) {
        return WHITE_TEX;
    }
}

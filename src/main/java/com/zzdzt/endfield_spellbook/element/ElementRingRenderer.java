package com.zzdzt.endfield_spellbook.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.client.fx.FxGeometry;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 元素附着弧环渲染器：目标脚下的四段弧（每段 = 1 层附着）。
 *
 * <p>四段弧各占 75°、段间 15° 间隙（明显区分）；已叠层段 = 元素色高亮，
 * 未叠层段 = 同色系暗色；整体缓慢正向旋转 + 微幅 shimmer。
 * 纯几何顶点色 + 纯白纹理加算，零新纹理。
 */
public class ElementRingRenderer extends EntityRenderer<ElementRingEntity> {

    private static final ResourceLocation WHITE_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    private static final RenderType GLOW_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("element_ring", WHITE_TEX);

    private static final float Y_OFFSET = 0.08f;
    private static final float ARC_WIDTH = 0.10f;
    private static final float ARC_SPAN_DEG = 75f;
    private static final float ARC_GAP_DEG = 15f;

    public ElementRingRenderer(Context context) {
        super(context);
    }

    @Override
    public void render(ElementRingEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        int lifetime = Math.max(1, entity.getLifetime());
        // 尾 10 tick 线性淡出（服务端 discard 前的视觉缓冲）
        float fade = Mth.clamp((lifetime - f) / 10f, 0f, 1f);
        float radius = entity.getRadius();
        int stacks = Math.min(4, Math.max(0, entity.getStacks()));
        float[] col = entity.getElement().particleColor();
        float[] dim = {col[0] * 0.22f, col[1] * 0.22f, col[2] * 0.30f};

        VertexConsumer consumer = bufferSource.getBuffer(GLOW_TYPE);
        float rot = f * 0.6f;

        // 薄衬环（全圆细线，暗色，撑出"地面 UI"层次）
        for (int i = 0; i < 8; i++) {
            float start = rot * 0.4f + i * 45f;
            FxGeometry.arc(poseStack, consumer, radius * 1.06f,
                start, start + 42f, 3, Y_OFFSET, 0.03f, 0.22f * fade, dim);
        }

        // 四段弧：每段 75° + 15° 间隙
        for (int i = 0; i < 4; i++) {
            float start = rot + i * (ARC_SPAN_DEG + ARC_GAP_DEG);
            float shimmer = 0.85f + 0.15f * Mth.sin((float) Math.toRadians(start) * 3f + f * 0.2f);
            if (i < stacks) {
                // 已叠层：元素色高亮
                FxGeometry.arc(poseStack, consumer, radius,
                    start, start + ARC_SPAN_DEG, 4, Y_OFFSET, ARC_WIDTH, 0.85f * shimmer * fade, col);
            } else {
                // 空槽：暗色低亮
                FxGeometry.arc(poseStack, consumer, radius,
                    start, start + ARC_SPAN_DEG, 4, Y_OFFSET, ARC_WIDTH, 0.28f * shimmer * fade, dim);
            }
        }

        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ElementRingEntity entity) {
        return WHITE_TEX;
    }
}

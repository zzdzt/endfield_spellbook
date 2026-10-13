package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 风矢穿林视觉编排门面：每帧构建 {@link WindArrowRenderState}，再把轨迹、
 * 前锋与命中三个渲染通道按固定顺序装配进 pose 树。只控制流程，不绘制细节。
 *
 * <p>RenderType 绑定与共享绘制原语（sprite/emitVertex）集中在此，
 * 供同包三个子渲染器复用；不负责射线命中或伤害结算。
 */
public final class WindArrowVfxRenderer {
    private static final ResourceLocation TEXTURE_WHITE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    static final RenderType WIND_ARROW_GLOW =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("wind_arrow_glow", TEXTURE_WHITE);

    private static final ResourceLocation TEXTURE_WIND_ARROW_NEEDLE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/particle/wind_arrow_needle.png");
    static final RenderType WIND_ARROW_NEEDLE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_needle", TEXTURE_WIND_ARROW_NEEDLE, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_BEAM =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_beam.png");
    static final RenderType WIND_ARROW_BEAM =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_beam", TEXTURE_WIND_ARROW_BEAM, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_RING =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_swirl_trail.png");
    static final RenderType WIND_ARROW_RING =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_ring", TEXTURE_WIND_ARROW_RING, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_CONSTELLATION =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_constellation.png");
    static final RenderType WIND_ARROW_CONSTELLATION =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_constellation", TEXTURE_WIND_ARROW_CONSTELLATION, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_IMPACT =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_impact.png");
    static final RenderType WIND_ARROW_IMPACT =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_impact", TEXTURE_WIND_ARROW_IMPACT, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_IMPACT_FINAL =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_impact_final.png");
    static final RenderType WIND_ARROW_IMPACT_FINAL =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_impact_final", TEXTURE_WIND_ARROW_IMPACT_FINAL, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_SPRITES =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_star_glint.png");
    static final RenderType WIND_ARROW_SPRITES =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_sprites", TEXTURE_WIND_ARROW_SPRITES, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_SHARDS =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_shards.png");
    static final RenderType WIND_ARROW_SHARDS =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_shards", TEXTURE_WIND_ARROW_SHARDS, false);

    private static final ResourceLocation TEXTURE_WIND_ARROW_HEAD =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_head.png");
    static final RenderType WIND_ARROW_HEAD =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_head", TEXTURE_WIND_ARROW_HEAD, false);

    private WindArrowVfxRenderer() {
    }

    public static void render(LizhiYanBlastVisualEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        if (entity.distance <= 0.001f) {
            return;
        }

        WindArrowRenderState state = WindArrowRenderState.create(entity, partialTicks);
        if (state.isExpired()) {
            return;
        }

        // 关键：先在世界轴坐标中生成 camera-facing ribbon，避免经过 yaw/pitch 旋转后变成侧边线。
        WindArrowTrailRenderer.renderMainRibbon(state, poseStack, bufferSource);

        // 弧带、星点、前锋与命中围绕弹道局部坐标系展开。
        poseStack.pushPose();
        poseStack.translate(0, entity.getBoundingBox().getYsize() * 0.5f, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot() - 90.0F));

        WindArrowTrailRenderer.renderSwirlTrail(state, poseStack, bufferSource);
        WindArrowTrailRenderer.renderStarGlint(state, poseStack, bufferSource);
        WindArrowTrailRenderer.renderAfterglow(state, poseStack, bufferSource);
        WindArrowHeadRenderer.renderHead(state, poseStack, bufferSource);
        WindArrowImpactRenderer.renderImpact(state, entity, poseStack, bufferSource);

        poseStack.popPose();
    }

    /** 沿弹道轴放置一张横向旋转的星芒/碎片贴图。 */
    static void drawWindArrowSprite(PoseStack poseStack, VertexConsumer consumer,
                                    float cx, float cy, float cz,
                                    float halfWidth, float halfHeight, int sprite,
                                    float rotationDegrees, float red, float green,
                                    float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        drawWindArrowSpriteAtOrigin(poseStack, consumer, halfWidth, halfHeight, sprite,
            red, green, blue, alpha);
        poseStack.popPose();
    }

    /**
     * 绘制 wind_arrow_star_glint.png 的单格 atlas。
     * atlas 为 4 列、每列占 0.25 的 U 区间；透明边缘依赖线性过滤柔化。
     */
    static void drawWindArrowSpriteAtOrigin(PoseStack poseStack, VertexConsumer consumer,
                                            float halfWidth, float halfHeight, int sprite,
                                            float red, float green, float blue, float alpha) {
        if (sprite < 0 || sprite > 3 || halfWidth <= 0.0f || halfHeight <= 0.0f || alpha <= 0.0f) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite * 0.25f;
        float u1 = u0 + 0.25f;
        // 每个 atlas tile 高度覆盖完整 V；顶底 UV 反向以与现有纹理方向保持一致。
        float v0 = 0.0f;
        float v1 = 1.0f;

        emitVertex(pose, consumer, -halfWidth, -halfHeight, 0.0f, u0, v1,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, halfWidth, -halfHeight, 0.0f, u1, v1,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, halfWidth, halfHeight, 0.0f, u1, v0,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
        emitVertex(pose, consumer, -halfWidth, halfHeight, 0.0f, u0, v0,
            red, green, blue, alpha, 0.0f, 0.0f, 1.0f);
    }

    static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer,
                           float x, float y, float z, float u, float v,
                           float red, float green, float blue, float alpha,
                           float nx, float ny, float nz) {
        consumer.vertex(pose.pose(), x, y, z)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(pose.normal(), nx, ny, nz)
            .endVertex();
    }
}

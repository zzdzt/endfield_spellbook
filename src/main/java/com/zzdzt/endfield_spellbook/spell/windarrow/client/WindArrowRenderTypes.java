package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 风矢视觉的 RenderType 注册：全部为加算无背面剔除材质，按视觉通道命名。
 * 底层缓存键字符串保持历史命名（wind_arrow_*），运行时行为与拆分前完全一致。
 */
final class WindArrowRenderTypes {

    private WindArrowRenderTypes() {
    }

    private static final ResourceLocation TEXTURE_WHITE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    /** 通用白点辉光（afterglow 星链、前锋楔形）。 */
    static final RenderType GLOW =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("wind_arrow_glow", TEXTURE_WHITE);

    private static final ResourceLocation TEXTURE_NEEDLE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/particle/wind_arrow_needle.png");
    static final RenderType NEEDLE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_needle", TEXTURE_NEEDLE, false);

    private static final ResourceLocation TEXTURE_BEAM =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_beam.png");
    static final RenderType BEAM =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_beam", TEXTURE_BEAM, false);

    private static final ResourceLocation TEXTURE_SWIRL =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_swirl_trail.png");
    static final RenderType RING =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_ring", TEXTURE_SWIRL, false);

    private static final ResourceLocation TEXTURE_CONSTELLATION =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_constellation.png");
    static final RenderType CONSTELLATION =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_constellation", TEXTURE_CONSTELLATION, false);

    private static final ResourceLocation TEXTURE_IMPACT =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_impact.png");
    static final RenderType IMPACT =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_impact", TEXTURE_IMPACT, false);

    private static final ResourceLocation TEXTURE_IMPACT_FINAL =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_impact_final.png");
    static final RenderType IMPACT_FINAL =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_impact_final", TEXTURE_IMPACT_FINAL, false);

    private static final ResourceLocation TEXTURE_STAR_GLINT =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_star_glint.png");
    static final RenderType SPRITES =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_sprites", TEXTURE_STAR_GLINT, false);

    private static final ResourceLocation TEXTURE_SHARDS =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_shards.png");
    static final RenderType SHARDS =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_shards", TEXTURE_SHARDS, false);

    private static final ResourceLocation TEXTURE_HEAD =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/vfx/wind_arrow_head.png");
    static final RenderType HEAD =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "wind_arrow_head", TEXTURE_HEAD, false);
}

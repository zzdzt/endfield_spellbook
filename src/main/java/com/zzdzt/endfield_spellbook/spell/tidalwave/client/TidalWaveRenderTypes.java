package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 踏潮卷浪 RenderType 缓存（≈WindArrowRenderTypes）：包私有。
 * 几何带统一走项目既有纯白贴图 + 顶点色，无新贴图。
 */
final class TidalWaveRenderTypes {

    private static final ResourceLocation TEXTURE_WHITE = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    /** 半透明水带（noCull 双面）。 */
    static RenderType ring() {
        return Holder.RING;
    }

    /** 加法高光（noCull 双面）。 */
    static RenderType glow() {
        return Holder.GLOW;
    }

    /** EntityRenderer#getTextureLocation 占位。 */
    static ResourceLocation textureWhite() {
        return TEXTURE_WHITE;
    }

    /** 懒加载：首次渲染调用时才创建 RenderType。 */
    private static final class Holder {
        private static final RenderType RING = RenderHelper.CustomerRenderType.magicNoCull(TEXTURE_WHITE);
        private static final RenderType GLOW = RenderHelper.CustomerRenderType.darkGlow(TEXTURE_WHITE);
    }

    private TidalWaveRenderTypes() {
    }
}

package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WhirlpoolEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 涡流渲染入口。
 * Entity 生命周期保持不变，所有视觉逻辑交给 TidalWaveVfxRenderer。
 */
public class WhirlpoolRenderer extends EntityRenderer<WhirlpoolEntity> {

    public WhirlpoolRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(WhirlpoolEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(WhirlpoolEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        TidalWaveVfxRenderer.renderWhirlpool(entity,
            new TidalWaveRenderContext(poseStack, bufferSource, partialTicks));
    }

    @Override
    public ResourceLocation getTextureLocation(WhirlpoolEntity entity) {
        return TidalWaveRenderTypes.textureWhite();
    }
}

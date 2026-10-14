package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WaterTornadoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 水龙卷渲染器：EntityRenderer 接入点，实际绘制委托 {@link TidalWaveVfxRenderer}。
 */
public class WaterTornadoRenderer extends EntityRenderer<WaterTornadoEntity> {

    public WaterTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(WaterTornadoEntity entity, Frustum camera,
                                double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(WaterTornadoEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        TidalWaveVfxRenderer.renderWaterTornado(entity,
            new TidalWaveRenderContext(poseStack, bufferSource, partialTicks));
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WaterTornadoEntity entity) {
        return TidalWaveRenderTypes.textureWhite();
    }
}

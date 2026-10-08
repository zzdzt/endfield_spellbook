package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;

public class LizhiYanBlastRenderer extends EntityRenderer<LizhiYanBlastVisualEntity> {

    public static final ModelLayerLocation MODEL_LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "lizhi_yan_blast_model"),
        "main"
    );

    private static final ResourceLocation TEXTURE_CORE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/lizhi_yan/core.png");
    private static final ResourceLocation TEXTURE_OVERLAY =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/lizhi_yan/overlay.png");

    private final ModelPart body;

    public LizhiYanBlastRenderer(Context context) {
        super(context);
        ModelPart modelpart = context.bakeLayer(MODEL_LAYER_LOCATION);
        this.body = modelpart.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-8, -16, -8, 16, 32, 16),
            PartPose.ZERO);
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public boolean shouldRender(LizhiYanBlastVisualEntity entity, Frustum camera,
                                   double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(LizhiYanBlastVisualEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();

        float lifetime = LizhiYanBlastVisualEntity.LIFETIME;
        float scalar = 0.25f;
        float length = 32 * scalar * scalar;
        float f = entity.tickCount + partialTicks;

        poseStack.translate(0, entity.getBoundingBox().getYsize() * 0.5f, 0);

        // 使用实体自身的旋转
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot() - 90));
        poseStack.scale(scalar, scalar, scalar);

        float alpha = Mth.clamp(1f - f / lifetime, 0, 1);

        // 获取颜色色调
        float[] tint = entity.getTintColor();

        // 沿视线方向堆叠模型
        for (float i = 0; i < entity.distance * 4; i += length) {
            poseStack.translate(0, length, 0);

            // 内层
            VertexConsumer consumer = bufferSource.getBuffer(
                RenderHelper.CustomerRenderType.darkGlow(TEXTURE_CORE));
            {
                poseStack.pushPose();
                float expansion = Mth.clampedLerp(1, 0, f / (lifetime - 5));
                poseStack.scale(expansion, 1, expansion);
                poseStack.mulPose(Axis.YP.rotationDegrees(f * -10));
                this.body.render(poseStack, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    tint[0], tint[1], tint[2], alpha * 0.8f);
                poseStack.popPose();
            }

            // 外层
            consumer = bufferSource.getBuffer(
                RenderHelper.CustomerRenderType.magicNoCull(TEXTURE_OVERLAY));
            {
                poseStack.pushPose();
                float expansion = Mth.clampedLerp(1.2f, 0, f / lifetime);
                poseStack.mulPose(Axis.YP.rotationDegrees(f * 5));
                poseStack.scale(expansion, 1, expansion);
                poseStack.mulPose(Axis.YP.rotationDegrees(45));
                this.body.render(poseStack, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    tint[0], tint[1], tint[2], alpha * 0.5f);
                poseStack.popPose();
            }
        }

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LizhiYanBlastVisualEntity entity) {
        return TEXTURE_CORE;
    }
}

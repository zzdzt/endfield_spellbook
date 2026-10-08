package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * LizhiYan 飞剑渲染器
 */
public class LizhiYanRenderer extends EntityRenderer<LizhiYanEntity> {

    // 飞剑纹理
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/item/lizhiyan_sword.png");

    private final LizhiYanSwordModel model;

    public LizhiYanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.model = new LizhiYanSwordModel(context.bakeLayer(LizhiYanSwordModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(LizhiYanEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(LizhiYanEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        int phaseId = entity.getAttackPhase();
        float chargeScale = entity.getChargeScale();
        float aimYaw = entity.getAimYaw();
        float aimPitch = entity.getAimPitch();

        poseStack.pushPose();

        // 位置微调
        poseStack.translate(0, 0.1, 0);

        // 根据实体朝向旋转
        if (phaseId == 6) { // SPIN
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        } else if (phaseId == 0) { // IDLE - 待机时不翻转，把手向下
            poseStack.mulPose(Axis.YP.rotationDegrees(-aimYaw + 90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(aimPitch));
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        } else { // CHARGE, STAB, BLAST, BLAST_DELAY, COOLDOWN - 其他状态需要翻转
            poseStack.mulPose(Axis.YP.rotationDegrees(-aimYaw + 90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(aimPitch));
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        }

        float scale = 0.15f * chargeScale;
        poseStack.scale(scale, scale, scale);

        // 光照：蓄力时发光
        int light = packedLight;
        if (phaseId == 1) { // CHARGE
            light = 15728880;
        }

        // 用实体模型渲染
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(
            poseStack,
            vertexConsumer,
            light,
            OverlayTexture.NO_OVERLAY,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}

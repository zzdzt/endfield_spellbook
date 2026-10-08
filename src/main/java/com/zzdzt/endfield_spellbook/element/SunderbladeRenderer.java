package com.zzdzt.endfield_spellbook.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;

/**
 * 青霆剑渲染器：雷炁凝形的插地能量剑（庄方宜「雷气凝剑、不仗凡铁」）。
 *
 * <p>独立剑形模型（几何拼装，非飞剑体系）：细刃直插入地 + 护手 + 柄尾。
 * 双层渲染（演出计划 4.3）：深青黑剑体层（cutout，给剑真实剪影）+ 加算辉光层
 * （白芯高亮 + 放大的青色轮廓晕 = 发光边）；放电瞬间（STRIKE_AT 到点后 5 tick）亮闪至白。
 * 朝向由 index 决定（剑面朝向目标圆心）。
 */
public class SunderbladeRenderer extends EntityRenderer<SunderbladeEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "sunderblade"),
            "main");

    private static final ResourceLocation WHITE_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    private static final RenderType CUTOUT_TYPE = RenderType.entityCutoutNoCull(WHITE_TEX);
    private static final RenderType GLOW_TYPE =
        com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly(
            "sunderblade", WHITE_TEX);

    // 剑体配色：深青黑体 + 白芯 + 青边（雷气凝剑、不仗凡铁）
    private static final float[] COL_BODY = {0.08f, 0.14f, 0.18f};
    private static final float[] COL_CORE = {0.88f, 0.97f, 1.00f};
    private static final float[] COL_EDGE = {0.31f, 0.85f, 1.00f};
    private static final float[] COL_FLASH = {0.90f, 0.98f, 1.00f};
    private static final float[] COL_GRIP = {0.20f, 0.32f, 0.40f};

    /** 青边轮廓晕相对剑体的放大倍数（辉光外溢形成发光边）。 */
    private static final float EDGE_SCALE = 1.35f;

    private final ModelPart root;

    public SunderbladeRenderer(Context context) {
        super(context);
        this.root = context.bakeLayer(LAYER_LOCATION);
    }

    /** 独立剑形：剑尖入地（y -0.15），刃身直上，护手 + 柄 + 柄尾。 */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("blade", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.05f, -0.15f, -0.05f, 0.1f, 1.15f, 0.1f),
            PartPose.ZERO);
        root.addOrReplaceChild("guard", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.2f, 1.0f, -0.07f, 0.4f, 0.08f, 0.14f),
            PartPose.ZERO);
        root.addOrReplaceChild("grip", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.05f, 1.08f, -0.05f, 0.1f, 0.32f, 0.1f),
            PartPose.ZERO);
        root.addOrReplaceChild("pommel", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.08f, 1.4f, -0.08f, 0.16f, 0.08f, 0.16f),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(SunderbladeEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        int lifetime = Math.max(1, entity.getLifetime());
        float fade = Mth.clamp((lifetime - f) / 10f, 0f, 1f);
        if (fade <= 0.01f) {
            super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
            return;
        }

        // 放电闪亮：STRIKE_AT 到点后 5 tick 内白热拉满
        long strikeAt = entity.getStrikeAt();
        float sinceStrike = entity.level().getGameTime() + partialTicks - strikeAt;
        boolean flashing = strikeAt != Long.MAX_VALUE && sinceStrike >= 0 && sinceStrike < 5;
        float[] coreCol = flashing ? COL_FLASH : COL_CORE;
        float breathe = 0.40f + 0.12f * Mth.sin(f * 0.15f);
        float glowAlpha = (flashing ? 1.0f : breathe) * fade;

        // 生成凝形：前 6 tick 从 0 放大到 1（雷炁凝形），再乘整体 RENDER_SCALE
        float form = Mth.clamp(f / 6f, 0f, 1f) * SunderbladeEntity.RENDER_SCALE;

        // ===== 第 1 层：深青黑剑体（cutout，给剑真实剪影） =====
        // 注：cutout 无半透明（alpha<0.5 整片丢弃），淡出末段剑体先隐、辉光后散
        VertexConsumer body = bufferSource.getBuffer(CUTOUT_TYPE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.scale(form, form, form);
        renderPart(poseStack, body, root.getChild("blade"), COL_BODY, 1.0f);
        renderPart(poseStack, body, root.getChild("guard"), COL_GRIP, 1.0f);
        renderPart(poseStack, body, root.getChild("grip"), COL_GRIP, 1.0f);
        renderPart(poseStack, body, root.getChild("pommel"), COL_BODY, 1.0f);
        poseStack.popPose();

        // ===== 第 2 层：加算辉光（白芯 + 放大青边晕 = 发光边） =====
        VertexConsumer glow = bufferSource.getBuffer(GLOW_TYPE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.scale(form, form, form);

        // 青边轮廓晕：剑体等比放大 1.35，加算外溢到剪影之外
        poseStack.pushPose();
        poseStack.scale(EDGE_SCALE, EDGE_SCALE, EDGE_SCALE);
        renderPart(poseStack, glow, root.getChild("blade"), COL_EDGE, glowAlpha * 0.45f);
        poseStack.popPose();
        // 白芯刃身 + 护手/柄微光
        renderPart(poseStack, glow, root.getChild("blade"), coreCol, glowAlpha);
        renderPart(poseStack, glow, root.getChild("guard"), COL_EDGE, glowAlpha * 0.55f);
        renderPart(poseStack, glow, root.getChild("grip"), COL_EDGE, glowAlpha * 0.40f);
        renderPart(poseStack, glow, root.getChild("pommel"), COL_FLASH, glowAlpha * 0.55f);
        poseStack.popPose();

        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    private static void renderPart(PoseStack poseStack, VertexConsumer consumer, ModelPart part,
                                   float[] col, float alpha) {
        part.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            col[0], col[1], col[2], alpha);
    }

    @Override
    public boolean shouldRender(SunderbladeEntity entity, net.minecraft.client.renderer.culling.Frustum camera,
                                double camX, double camY, double camZ) {
        float s = SunderbladeEntity.RENDER_SCALE;
        return camera.isVisible(new AABB(
            entity.getX() - 1.5, entity.getY() - 2.0, entity.getZ() - 1.5,
            entity.getX() + 1.5, entity.getY() + 1.6 * s + 1.0, entity.getZ() + 1.5));
    }

    @Override
    public ResourceLocation getTextureLocation(SunderbladeEntity entity) {
        return WHITE_TEX;
    }
}

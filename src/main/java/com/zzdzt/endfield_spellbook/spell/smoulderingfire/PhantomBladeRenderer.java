package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 幻影魔剑渲染器：投影主手武器真实模型（任意武器通用）。
 * 挥砍的燃烧回环演出由 {@link FlameRingEntity} 的客户端粒子编排承担（无几何）。
 */
public class PhantomBladeRenderer extends EntityRenderer<PhantomBladeEntity> {

    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public PhantomBladeRenderer(Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(PhantomBladeEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        int duration = Math.max(1, entity.getDuration());

        // 淡入（前 2t 0.6→1）+ 收尾收缩（尾 4t 1→0.3）
        float fadeIn = Mth.clamp(f / 2f, 0f, 1f);
        float fadeOut = Mth.clamp((duration - f) / 4f, 0f, 1f);
        float scale = (0.6f + 0.4f * fadeIn) * (0.3f + 0.7f * fadeOut);

        ItemStack stack = entity.getDisplayStack();
        if (!stack.isEmpty() && scale > 0.05f) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            // 弧面俯仰：切向含竖直分量时剑随斜面弧线翘起/压低（抬头砍 → 剑指天空）
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            poseStack.scale(scale, scale, scale);
            // 斜劈姿态：向后倾倒 50°（半躺）+ 面内翻转 180 → 剑尖朝下前方（下劈）
            poseStack.mulPose(Axis.XP.rotationDegrees(-50f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            renderItem(poseStack, itemRenderer, stack, light, bufferSource, entity.level(), entity.getId());
            poseStack.popPose();
        }

        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    /**
     * 渲染物品模型（居中 + 单份）。
     * 关键：1.20.1 的 renderStatic(FIXED) 内部【没有】-0.5 居中（居中由调用方负责，
     * 同 ItemFrame）——外部必须 translate(-0.5,-0.5,-0.5)，否则模型偏到一个象限，
     * 表现为"两把武器 / 位置偏移"。
     */
    public static void renderItem(PoseStack poseStack,
                                  net.minecraft.client.renderer.entity.ItemRenderer itemRenderer,
                                  ItemStack stack, int light, MultiBufferSource bufferSource,
                                  Level level, int seed) {
        poseStack.pushPose();
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light,
            OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, seed);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(PhantomBladeEntity entity, net.minecraft.client.renderer.culling.Frustum camera,
                                double camX, double camY, double camZ) {
        return camera.isVisible(new AABB(
            entity.getX() - 4, entity.getY() - 2, entity.getZ() - 4,
            entity.getX() + 4, entity.getY() + 3, entity.getZ() + 4));
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomBladeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

package com.zzdzt.endfield_spellbook.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import com.mojang.math.Axis;

/**
 * 目标标记环渲染器：水墨印记贴图（青霆剑诀引导标记）。
 *
 * <p>直接使用泼墨环贴图（textures/entity/target_mark.png，带透明通道）
 * 铺在目标脚下，整体缓旋 + 呼吸；正常 alpha 混合线性过滤（柔边）。
 * 生成首 5 tick 半径由 1.15x 收拢到 1x（墨迹落纸感），尾 10 tick 淡出。
 */
public class TargetMarkRenderer extends EntityRenderer<TargetMarkEntity> {

    private static final ResourceLocation TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/target_mark.png");

    /** 原版半透明实体层（史莱姆外层同款，Embeddium/Oculus 下经过大规模验证）。 */
    private static final RenderType INK_TYPE = RenderType.entityTranslucent(TEX);

    /** 整体缓旋速度（°/tick，占位待调）。 */
    private static final float SPIN_PER_TICK = 0.2f;
    /** 呼吸明暗幅度。 */
    private static final float BREATHE = 0.01f;
    /** 整体半径倍率（占位，已调 +50%）。 */
    private static final float RADIUS_MULT = 1.5f;
    /** 整体透明度倍率（占位，已调 -50%）。 */
    private static final float ALPHA_MULT = 0.7f;

    private static final float Y_OFFSET = 0.08f;
    /** 墨迹收拢时长（tick）。 */
    private static final float SETTLE_TICKS = 5f;

    public TargetMarkRenderer(Context context) {
        super(context);
    }

    @Override
    public void render(TargetMarkEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        int lifetime = Math.max(1, entity.getLifetime());
        float fade = Mth.clamp((lifetime - f) / 10f, 0f, 1f);
        if (fade <= 0.01f) {
            super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
            return;
        }
        float radius = entity.getRadius();

        // 墨迹收拢：首 5 tick 半径 1.15x → 1x
        float settle = Mth.clamp(1f - f / SETTLE_TICKS, 0f, 1f);
        float scale = (radius * RADIUS_MULT * (1f + 0.15f * settle)) * 2f; // quad 边长（贴图环铺满整张）

        // 呼吸明暗（不随旋转变）
        float alpha = (1f - BREATHE + BREATHE * Mth.sin(f * 0.2f)) * fade * ALPHA_MULT;

        VertexConsumer ink = bufferSource.getBuffer(INK_TYPE);
        poseStack.pushPose();
        poseStack.translate(0, Y_OFFSET, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * SPIN_PER_TICK));
        poseStack.scale(scale, 1f, scale);
        PoseStack.Pose mat = poseStack.last();
        groundQuad(mat, ink, alpha);
        poseStack.popPose();

        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    /** 水平 quad（XZ 平面，贴图 UV 全铺），原点在中心；须走 pose 矩阵（实体平移在矩阵里）。 */
    private static void groundQuad(PoseStack.Pose mat, VertexConsumer c, float alpha) {
        vertex(mat, c, -0.5f, -0.5f, 0f, 0f, alpha);
        vertex(mat, c, 0.5f, -0.5f, 1f, 0f, alpha);
        vertex(mat, c, 0.5f, 0.5f, 1f, 1f, alpha);
        vertex(mat, c, -0.5f, 0.5f, 0f, 1f, alpha);
    }

    private static void vertex(PoseStack.Pose mat, VertexConsumer c, float x, float z, float u, float v, float alpha) {
        // 黑墨染色：白墨贴图 × 近黑顶点色 = 黑墨环（灰石地上对比最强）
        c.vertex(mat.pose(), x, Y_OFFSET, z)
            .color(0.06f, 0.07f, 0.08f, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(mat.normal(), 0, 1, 0)
            .endVertex();
    }

    @Override
    public boolean shouldRender(TargetMarkEntity entity, net.minecraft.client.renderer.culling.Frustum camera,
                                double camX, double camY, double camZ) {
        float r = entity.getRadius() * RADIUS_MULT * 1.4f + 0.5f;
        return camera.isVisible(new AABB(
            entity.getX() - r, entity.getY() - 0.3, entity.getZ() - r,
            entity.getX() + r, entity.getY() + 0.5, entity.getZ() + r));
    }

    @Override
    public ResourceLocation getTextureLocation(TargetMarkEntity entity) {
        return TEX;
    }
}

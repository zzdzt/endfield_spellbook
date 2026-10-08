package com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.client.fx.FxGeometry;
import com.zzdzt.endfield_spellbook.client.post.LncSnapshot;
import com.zzdzt.endfield_spellbook.client.post.PipelinePost;
import com.zzdzt.endfield_spellbook.client.post.PostRenderTypes;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 压缩液氮炮弹渲染器（温蒂 S3 对齐）。
 *
 * 飞行阶段：
 *   - 双层球体：白核（加算高亮）+ 冰蓝薄雾壳（低 alpha 包裹，压缩液氮的"水膜"感）
 *   - 冷雾拖尾：渲染器逐帧记录历史位置，polylineRibbon 头粗尾细光带
 * 命中阶段（RING_TICKS 内）：
 *   - 贴地扩散双环（easeOut 半径外扩 + alpha 衰减，冻土涟漪）
 *   - 爆心白青闪光 billboard 收缩淡出
 *
 * 双路径：后处理管线启用时实体 pass 只冻结快照（AFTER_LEVEL 重绘进自研 FBO +
 * bloom），否则走原版加算 fallback（PARTICLES_TARGET）。
 */
public class LncProjectileRenderer extends EntityRenderer<LncProjectileEntity> {

    // 纯白纹理：颜色完全由顶点决定
    private static final ResourceLocation WHITE_TEX =
        ResourceLocation.fromNamespaceAndPath("endfield_spellbook", "textures/entity/soul_orb/white.png");

    private static final RenderType VANILLA_BODY =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly("lnc_body", WHITE_TEX);

    // 色板：白核 → 冰蓝 → 深冰蓝（整体低亮度：冷雾而非强光）
    private static final float[] COL_CORE = {0.80f, 0.90f, 0.97f};
    private static final float[] COL_SHELL = {0.42f, 0.68f, 0.92f};
    private static final float[] COL_DEEP = {0.18f, 0.42f, 0.85f};

    private static final float CORE_RADIUS = 0.32f;
    private static final float SHELL_RADIUS = 0.50f;
    private static final int TRAIL_POINTS = 8;

    /** 渲染器单例的 per-entity 状态：拖尾历史 + 命中起始帧。 */
    private static final class TrailState {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        Float impactStartF;
    }

    private final Map<Integer, TrailState> trails = new HashMap<>();

    public LncProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(LncProjectileEntity entity) {
        return WHITE_TEX;
    }

    @Override
    public void render(LncProjectileEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        TrailState state = trails.computeIfAbsent(entity.getId(), k -> new TrailState());
        updateTrail(state, entity, partialTicks);

        // 后处理管线：实体 pass 冻结快照，AFTER_LEVEL 统一重绘进自研 FBO
        if (PipelinePost.isActive()) {
            if (state.impactStartF == null && entity.getPhase() == LncProjectileEntity.PHASE_IMPACT) {
                state.impactStartF = f;
            }
            PipelinePost.enqueue(new LncSnapshot(entity, f));
            super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
            return;
        }

        poseStack.pushPose();
        drawBody(entity, f, state, poseStack, bufferSource, VANILLA_BODY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    /**
     * 后处理管线重绘入口（PipelinePost 在 AFTER_LEVEL 调用）。
     * poseStack 已平移到实体位置（相机为原点），与实体 pass 一致。
     */
    public void drawForPipeline(LncProjectileEntity entity, float f,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                RenderType glow) {
        TrailState state = trails.get(entity.getId());
        if (state == null) {
            state = new TrailState();
            trails.put(entity.getId(), state);
        }
        drawBody(entity, f, state, poseStack, bufferSource, glow);
    }

    // ===== 绘制主体（双路径共用）=====

    private void drawBody(LncProjectileEntity entity, float f, TrailState state,
                          PoseStack poseStack, MultiBufferSource bufferSource, RenderType type) {
        VertexConsumer vc = bufferSource.getBuffer(type);
        boolean impact = entity.getPhase() == LncProjectileEntity.PHASE_IMPACT;
        float impactStart = impact && state.impactStartF != null ? state.impactStartF : f;

        if (!impact) {
            // 双层球体：白核 + 冰蓝雾壳（压缩液氮"水膜"，低亮度冷雾感）
            FxGeometry.sphere(poseStack, vc, CORE_RADIUS, COL_CORE, 0.55f, 6, 12);
            FxGeometry.sphere(poseStack, vc, SHELL_RADIUS, COL_SHELL, 0.16f, 6, 12);

            // 冷雾拖尾：头粗尾细光带（点为实体相对坐标）
            List<Vector3f> path = toLocalPath(state, entity);
            if (path.size() >= 2) {
                FxGeometry.polylineRibbon(poseStack, vc, path,
                    progress -> (0.16f) * (1.0f - 0.7f * progress),   // 头粗尾细
                    progress -> 0.32f * (1.0f - progress),            // 尾端渐隐
                    1.0f, COL_SHELL[0], COL_SHELL[1], COL_SHELL[2], 0.35f, 0.65f);
            }
        } else {
            // ===== 命中演出 =====
            float progress = Mth.clamp((f - impactStart) / LncProjectileEntity.RING_TICKS, 0f, 1f);
            float easeOut = 1f - (1f - progress) * (1f - progress);
            float radius = entity.getExplosionRadius() * easeOut;
            float alpha = (1f - progress);

            // 贴地扩散双环（冻土涟漪）：主环亮 + 内侧细环暗
            FxGeometry.arc(poseStack, vc, radius, 0f, 360f, 24, 0.05f, 0.14f, alpha * 0.50f, COL_SHELL);
            FxGeometry.arc(poseStack, vc, radius * 0.72f, 0f, 360f, 20, 0.08f, 0.05f, alpha * 0.28f, COL_DEEP);

            // 爆心闪光：billboard 收缩淡出
            float flashSize = 1.1f * (1f - progress) + 0.15f;
            FxGeometry.billboardQuad(poseStack, vc, flashSize, 0f, alpha * 0.42f, COL_CORE);
        }
    }

    // ===== 拖尾状态 =====

    /** 逐帧 push 当前插值位置（世界坐标），裁剪到 TRAIL_POINTS。两路径共用。 */
    private void updateTrail(TrailState state, LncProjectileEntity entity, float partialTicks) {
        if (entity.isRemoved()) {
            trails.remove(entity.getId());
            return;
        }
        // 定期清一次离开视野的旧弹缓存（discard 后 isRemoved 生效会自清，双保险）
        if (trails.size() > 64) {
            trails.entrySet().removeIf(e -> e.getValue() == state && entity.isRemoved());
        }
        if (entity.getPhase() != LncProjectileEntity.PHASE_FLYING) {
            return;
        }
        Vec3 interpolated = new Vec3(
            Mth.lerp(partialTicks, entity.xOld, entity.getX()),
            Mth.lerp(partialTicks, entity.yOld, entity.getY()),
            Mth.lerp(partialTicks, entity.zOld, entity.getZ()));
        ArrayDeque<Vec3> points = state.points;
        if (points.isEmpty() || points.peekLast().distanceToSqr(interpolated) > 1e-6) {
            points.addLast(interpolated);
        }
        while (points.size() > TRAIL_POINTS) {
            points.pollFirst();
        }
    }

    /** 世界坐标点列 → 实体相对坐标（polylineRibbon 的 path 约定），旧 → 新。 */
    private List<Vector3f> toLocalPath(TrailState state, LncProjectileEntity entity) {
        List<Vector3f> path = new ArrayList<>(state.points.size());
        Vec3 origin = entity.position();
        for (Vec3 p : state.points) {
            path.add(new Vector3f(
                (float) (p.x - origin.x),
                (float) (p.y - origin.y),
                (float) (p.z - origin.z)));
        }
        // 头端补当前插值位置（光带贴住弹体）
        path.add(new Vector3f(0f, 0f, 0f));
        return path;
    }
}

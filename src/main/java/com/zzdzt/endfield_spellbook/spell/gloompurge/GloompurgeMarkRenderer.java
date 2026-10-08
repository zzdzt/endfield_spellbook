package com.zzdzt.endfield_spellbook.spell.gloompurge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.client.fx.FxGeometry;
import com.zzdzt.endfield_spellbook.client.fx.FxLod;
import com.zzdzt.endfield_spellbook.client.post.MarkSnapshot;
import com.zzdzt.endfield_spellbook.client.post.PipelinePost;
import com.zzdzt.endfield_spellbook.client.post.PostRenderTypes;
import com.zzdzt.endfield_spellbook.renderer.EndfieldRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 破晦阵几何印记渲染器（终末地「诀·破晦阵」终结技构图）。
 *
 * 薄片 quad 拼出矢量线条/面片，加算合成自发光；全息冰蓝配色：
 *   - 径向渐变：内白青 → 中青 → 外深冰蓝，中心能量感最强
 *   - 角度 shimmer：透明度随方位角+时间微幅波动，全息薄膜感
 *   - 整体低 alpha 加算叠亮，透明能量场而非实线
 *
 * 形状：
 *   - 蓄力三角：半径随 tick 逐帧收缩（scale = 1 - f/lifetime）
 *   - 领域边缘（终末地构图，外→内）：
 *     · 能量雾边界：单层低矮雾幕（稀疏竖向条纹 + 涌动呼吸 + 方位角错峰 +
 *       三段色调深蓝→青→白），近乎透明；上缘由蒸发点云掩盖
 *     · 电路板全息平台：内圈覆盖领域 + 外圈更淡延展到域外（动态生成 PCB 纹理：
 *       面板网格 + 随机断线 + 中心双环，双环扩大到八卦阵外围不重叠）
 *     · 地面阵 UI：反向虚线弧环 + 内圈差速伴环 + 仪表化三级刻度（主/次/微）
 *       + 旋转扫描亮弧（带淡色拖尾），整体呼吸脉冲
 *     · 线框全息楼阁塔 ×4：均匀坐落在地面 UI 环边界，台基 → 角柱/梁环/腰线塔身
 *       → 飞檐 → 塔刹十字，自下而上扫描生长（materialization），
 *       高度/层数/朝向由实体种子确定性散布
 *     · 上浮光块：金/白青/青三色 billboard 小方块（亮核 + 光晕双层），缓慢上升
 *   - 领域八卦阵：外环八边形 + 内环 + 后天八卦卦象（阳爻实线、阴爻断线），
 *     缓慢旋转，尾淡出与领域同帧（服务端同步 FADING 标志）
 */
public class GloompurgeMarkRenderer extends EntityRenderer<GloompurgeMarkEntity> {

    // 复用飞剑核心白膜作为加算纹理
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/lizhi_yan/core.png");

    private static final RenderType MARK_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCull("gloompurge_mark", TEXTURE);

    // 纯白加算纹理：颜色完全由顶点决定，用于辉光/符纹亮核
    private static final ResourceLocation WHITE_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

    // 3) 辉光/符纹/数据流：纯白纹理，颜色由顶点决定（塔阵/光块/蒸发点共用）
    private static final RenderType GLOW_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullParticlesColorOnly(
            "gloompurge_glow", WHITE_TEX);

    // 全息渐变调色板：内 → 外
    private static final float[] COL_INNER = {0.75f, 0.95f, 1.00f}; // 白青（核心高能）
    private static final float[] COL_MID   = {0.22f, 0.71f, 0.97f}; // 青
    private static final float[] COL_OUTER = {0.10f, 0.45f, 0.90f}; // 深冰蓝
    private static final float[] COL_SWEEP = {0.85f, 1.00f, 1.00f}; // 扫描/电波亮核白青
    // 道教金铜（暖焦点）与暗靛蓝（辅助骨架，冷）：
    // 与青色能量场形成冷暖对比，避免整阵单色导致的"草稿感"
    private static final float[] COL_GOLD  = {1.00f, 0.76f, 0.32f};
    private static final float[] COL_DEEP  = {0.12f, 0.28f, 0.80f};

    // 后天八卦方位序（离坤兑乾坎艮震巽），bit k = 第 k 爻（自内而外），1 阳 0 阴
    private static final int[] TRIGRAM_ORDER = {0b101, 0b000, 0b011, 0b111, 0b010, 0b100, 0b001, 0b110};

    private static final float Y_OFFSET = 0.06f;

    // 内圈半径占领域半径比例（中心八卦阵内环）
    private static final float BAGUA_INNER_SCALE = 0.55f;

    // ===== 终末地「诀·破晦阵」构图 =====
    // 参考图要素：线框全息楼阁塔 ×4 + 电路板全息地面平台 + 上浮光块。
    // 圆柱能量墙整体移除 —— 终末地领域没有墙，边界感由塔阵 + 地面 UI 环承担。

    // 塔高 = 领域半径 × 该比例（0.85 = 20 格半径对应 17 格高塔，压过普通建筑的天际线）
    private static final float TOWER_HEIGHT_SCALE = 0.85f;
    private static final int   TOWER_COUNT = 4;
    // 塔基建造演出：延迟起步 → 逐塔错峰 → 自下而上扫描生长
    private static final float TOWER_GROW_DELAY  = 6f;
    private static final float TOWER_STAGGER     = 5f;
    private static final float TOWER_BUILD_TICKS = 22f;
    // 上浮光块
    private static final int   CUBE_COUNT = 22;
    private static final float CUBE_START_TICK = 16f;
    // 地面平台外圈延展倍率（终末地平台延伸到领域之外的无边界感）
    private static final float PLATFORM_OUTER_SCALE = 1.7f;

    // 地面电路板平台纹理（运行时动态生成，无需资源文件）
    private static final ResourceLocation GROUND_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "dynamic/gloompurge_ground_platform");
    private static final RenderType GROUND_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinear("gloompurge_ground", GROUND_TEX);
    private static boolean groundPlatformReady = false;

    // 能量流动纹理（运行时动态生成）：两层错频斜向条纹叠加（打破单一正弦的机械感），
    // v 方向 8 周期、u 方向 3 周期 —— u/v 均取整数周期：圆柱 u=0/1 接缝连续，
    // 且两个方向都可无缝循环平移（配合 UV 偏移形成条纹涌动）
    private static final ResourceLocation MIST_TEX =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "dynamic/gloompurge_mist_flow");
    // 不写深度、双面渲染 → 内外表面叠加出包裹感；线性过滤采样条纹纹理
    private static final RenderType MIST_TYPE =
        EndfieldRenderTypes.entityAdditiveGlowNoCullLinearParticles(
            "gloompurge_mist", MIST_TEX, false);
    private static boolean mistTextureReady = false;

    // ===== 光影兼容 =====
    // 不做光影特判（用户决定）：非管线 fallback 一律走原版加算路径（VANILLA_SET）。
    // 光影下的适配由自研后处理管线承担（特效画进自研 FBO 绕开光影 gbuffer 语义）。

    /**
     * 一套渲染路径：四个 RenderType。
     * - VANILLA_SET：原版加算 + PARTICLES_TARGET（非管线 fallback）；
     * - 管线路径的 set 由 PostRenderTypes 提供（加算写自研 FBO + 自控 bloom）。
     */
    public record RenderTypeSet(RenderType mark, RenderType ground, RenderType mist, RenderType glow) {
    }

    private static final RenderTypeSet VANILLA_SET =
        new RenderTypeSet(MARK_TYPE, GROUND_TYPE, MIST_TYPE, GLOW_TYPE);

    // 尾淡出起点（与 TargetAreaRenderer 同款机制），非淡出期重置防跨实体残留
    private int fadeTick = -1;

    public GloompurgeMarkRenderer(Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(GloompurgeMarkEntity entity, Frustum camera,
                                double camX, double camY, double camZ) {
        // 塔阵按实体半径覆盖高度，其余形状留 1 格余量（贴地图形）
        float radius = entity.getRadius();
        boolean edge = entity.getShape() == GloompurgeMarkEntity.SHAPE_DOMAIN_EDGE;
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float top = edge ? radius * TOWER_HEIGHT_SCALE + 1.0f : 1.0f;
        // 边缘形状含外圈延展平台（方形 quad），对角线需留出倍率 × √2 余量防误剔除
        float hoz = edge ? radius * PLATFORM_OUTER_SCALE * Mth.SQRT_OF_TWO : radius;
        return camera.isVisible(new AABB(
            x - hoz, y - 1.0, z - hoz,
            x + hoz, y + top, z + hoz));
    }

    @Override
    public void render(GloompurgeMarkEntity entity, float yaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float f = entity.tickCount + partialTicks;
        float life = Math.max(1, entity.getLifetime());

        // 后处理管线启用：实体 pass 只冻结渲染参数，AFTER_LEVEL 统一重绘到自研 FBO
        if (PipelinePost.isActive()) {
            if (entity.getShape() == GloompurgeMarkEntity.SHAPE_DOMAIN_EDGE) {
                double dist = Minecraft.getInstance().gameRenderer.getMainCamera()
                    .getPosition().distanceTo(entity.position());
                PipelinePost.enqueue(new MarkSnapshot(entity, f, edgeAlpha(entity, f), dist,
                    entity.getShape()));
            } else {
                PipelinePost.enqueue(new MarkSnapshot(entity, f, edgeAlpha(entity, f), 0,
                    entity.getShape()));
            }
            return;
        }

        poseStack.pushPose();

        if (entity.getShape() == GloompurgeMarkEntity.SHAPE_CHARGE_TRIANGLE) {
            // 逐帧收缩汇聚
            VertexConsumer consumer = bufferSource.getBuffer(VANILLA_SET.mark());
            float scale = Mth.clamp(1f - f / life, 0f, 1f);
            float radius = entity.getRadius() * scale;
            FxGeometry.polygon(poseStack, consumer, 3, radius, entity.getRot(), Y_OFFSET, 0.12f, 0.7f, COL_SWEEP);
        } else {
            float alpha = edgeAlpha(entity, f);
            if (entity.getShape() == GloompurgeMarkEntity.SHAPE_DOMAIN_EDGE) {
                // 相机距离：每帧一次（领域边缘是大场面，供 LOD 决策）
                double dist = Minecraft.getInstance().gameRenderer.getMainCamera()
                    .getPosition().distanceTo(entity.position());
                renderEdge(entity, f, poseStack, bufferSource, alpha, dist, VANILLA_SET);
            } else {
                // 圆心八卦阵（SHAPE_DOMAIN_OCTAGON）：单一 mark buffer，段内取用
                renderBagua(entity, f, poseStack, bufferSource, alpha, VANILLA_SET);
            }
        }

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    /** 边缘淡出 alpha（fadeTick 机制：服务端同步 FADING 标志，10t 线性淡出）。 */
    private float edgeAlpha(GloompurgeMarkEntity entity, float f) {
        float alpha = 0.40f;
        if (entity.isFading()) {
            if (fadeTick < 0) {
                fadeTick = entity.tickCount;
            }
            alpha *= Mth.clampedLerp(1, 0, (f - fadeTick) / 10f);
        } else {
            fadeTick = -1;
        }
        return alpha;
    }

    /**
     * 后处理管线重绘入口（PipelinePost 在 AFTER_LEVEL 调用）。
     * 坐标系：poseStack 已由调用方平移到实体位置（相机为原点），与实体 pass 一致。
     */
    public void drawForPipeline(GloompurgeMarkEntity entity, float f, float alpha, double dist,
                                int shape, PoseStack poseStack, MultiBufferSource bufferSource,
                                RenderTypeSet set) {
        float life = Math.max(1, entity.getLifetime());
        if (shape == GloompurgeMarkEntity.SHAPE_CHARGE_TRIANGLE) {
            VertexConsumer consumer = bufferSource.getBuffer(set.mark());
            float scale = Mth.clamp(1f - f / life, 0f, 1f);
            float radius = entity.getRadius() * scale;
            FxGeometry.polygon(poseStack, consumer, 3, radius, entity.getRot(), Y_OFFSET, 0.12f, 0.7f, COL_SWEEP);
        } else if (shape == GloompurgeMarkEntity.SHAPE_DOMAIN_EDGE) {
            renderEdge(entity, f, poseStack, bufferSource, alpha, dist, set);
        } else {
            renderBagua(entity, f, poseStack, bufferSource, alpha, set);
        }
    }

    // 领域边缘：地面回声波纹 + 全息能量边界墙 + 反向虚线弧 + 刻度 + 扫描亮弧 + 呼吸脉冲

    // ===== 领域展开演出 =====
    // 领域从脚下（半径 0）向外生长到全额半径的缓动曲线。
    // 全阵各层（地面波纹 / 能量墙 / 虚线弧 / 刻度 / 扫描亮弧）都从 renderEdge 的 R 派生，
    // 因此只在 R 上乘一次展开系数即可让整阵同步生长，无需逐层改动。
    private static final float DOMAIN_EXPAND_TICKS = 40f; // 地面阵展开时长（tick，40 = 2 秒）

    /** easeOutCubic：起步快、末端平滑收敛到全额半径。 */
    private static float domainGrow(float f) {
        if (f >= DOMAIN_EXPAND_TICKS) return 1f;
        float t = Mth.clamp(f / DOMAIN_EXPAND_TICKS, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    // ===== 确定性哈希工具 =====
    // 塔阵布局 / 闪电抖动 / 光块散布全部由实体 id 播种 → 同一领域每次渲染恒同形，
    // 不逐帧乱跳；不同领域之间彼此错开。

    /** [0,1) 均匀散列（同 seed 同 i 恒同值）。 */
    private static float hash01(int seed, int i) {
        int h = seed * 31 + i * 17 + 0x9e3779b9;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    /** 双 int 散列 → [0,1)，纹理生成的格子级随机用。 */
    private static float hash2f(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h ^= h >>> 13;
        h *= 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    private void renderEdge(GloompurgeMarkEntity entity, float f, PoseStack poseStack,
                            MultiBufferSource bufferSource, float alpha, double dist, RenderTypeSet set) {
        // ① MARK：地面阵 UI（虚线弧环 / 差速伴环 / 三级刻度 / 扫描弧）
        VertexConsumer consumer = bufferSource.getBuffer(set.mark());
        // 半径展开：地面元素从脚下向外长出（纯视觉，判定半径不变）
        float R = entity.getRadius() * domainGrow(f);
        float pulse = 0.85f + 0.15f * Mth.sin(f * 0.15f);
        float aEdge = alpha * pulse;

        // 反向旋转虚线弧环（12 段，每段 20°），深冰蓝 + 角度 shimmer
        float rotBack = -f * 1.0f;
        for (int i = 0; i < 12; i++) {
            float start = rotBack + i * 30f;
            float shimmer = 0.7f + 0.3f * Mth.sin((float) Math.toRadians(start) * 3f + f * 0.2f);
            FxGeometry.arc(poseStack, consumer, R, start, start + 20f, 4, Y_OFFSET, 0.10f, aEdge * shimmer, COL_OUTER);
        }
        // 内圈差速伴环：18 段更细更暗、正向缓旋，与主虚线环形成反向差速视差
        float rotFwd = f * 0.6f;
        for (int i = 0; i < 18; i++) {
            float start = rotFwd + i * 20f;
            FxGeometry.arc(poseStack, consumer, R * 0.955f, start, start + 9f, 3, Y_OFFSET, 0.045f, aEdge * 0.55f, COL_DEEP);
        }
        // 仪表化三级刻度：8 主刻度（长亮渐变）+ 24 次刻度（短暗）+ 48 微刻度（发丝，高档近距）
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45f);
            float cx = (float) Math.cos(a);
            float cz = (float) Math.sin(a);
            float shimmer = 0.75f + 0.25f * Mth.sin((float) a * 2f + f * 0.22f);
            FxGeometry.edge(poseStack, consumer,
                cx * R * 0.90f, Y_OFFSET, cz * R * 0.90f,
                cx * R, Y_OFFSET, cz * R, 0.065f, aEdge * shimmer, COL_INNER, COL_MID);
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15f);
            float cx = (float) Math.cos(a);
            float cz = (float) Math.sin(a);
            float shimmer = 0.7f + 0.3f * Mth.sin((float) a * 4f + f * 0.25f);
            FxGeometry.edge(poseStack, consumer,
                cx * R * 0.945f, Y_OFFSET, cz * R * 0.945f,
                cx * R, Y_OFFSET, cz * R, 0.04f, aEdge * 0.8f * shimmer, COL_MID, COL_MID);
        }
        if (FxLod.glowLayer(dist)) {
            for (int i = 0; i < 48; i++) {
                double a = Math.toRadians(i * 7.5f + 3.75f); // 与次刻度错半格
                float cx = (float) Math.cos(a);
                float cz = (float) Math.sin(a);
                FxGeometry.edge(poseStack, consumer,
                    cx * R * 0.968f, Y_OFFSET, cz * R * 0.968f,
                    cx * R * 0.992f, Y_OFFSET, cz * R * 0.992f, 0.018f, aEdge * 0.5f, COL_OUTER, COL_OUTER);
            }
        }
        // 全息扫描亮弧：60° 高亮白青弧缓旋 + 后方 45° 淡色拖尾（彗尾感）
        float sweep = f * 2.0f;
        FxGeometry.arc(poseStack, consumer, R * 0.99f, sweep, sweep + 60f, 8, Y_OFFSET, 0.16f, alpha * 0.9f, COL_SWEEP);
        FxGeometry.arc(poseStack, consumer, R * 0.99f, sweep - 48f, sweep - 4f, 6, Y_OFFSET, 0.09f, alpha * 0.32f, COL_MID);

        // ② GROUND：电路板全息平台（内圈全场 + 外圈域外延展）
        renderGroundPlatform(R, f, poseStack, bufferSource, aEdge, set);

        // ③ MIST + GLOW：能量雾边界（单层流动呼吸雾幕）+ 立体结构（塔阵 → 浮立方）
        // ⚠️ 空 batch 铁律：雾幕与蒸发点在 R > 0.5 后恒有顶点
        if (R > 0.5f) {
            float mistH = R * MIST_HEIGHT_SCALE;
            VertexConsumer mist = bufferSource.getBuffer(set.mist());
            drawMistVeil(poseStack, mist, R, mistH, f, aEdge);
            VertexConsumer glow = bufferSource.getBuffer(set.glow());
            // 雾幕顶部蒸发点云（掩盖整齐的顶部渐变线）
            renderMistEvaporation(entity, f, poseStack, glow, aEdge);
            if (f > TOWER_GROW_DELAY + 0.1f) renderTowers(entity, f, poseStack, glow, alpha);
            if (f > CUBE_START_TICK + 0.1f) renderCubes(entity, f, poseStack, glow, alpha);
        }
    }

    // 电路板全息地面平台：两块贴地 quad 采样动态生成的 PCB 纹理，
    // 内圈覆盖领域、外圈更淡地延展到领域之外（终末地平台的无边界感），
    // 极慢呼吸让"场"保持活着质感。

    private void renderGroundPlatform(float R, float f, PoseStack poseStack,
                                      MultiBufferSource bufferSource, float alpha, RenderTypeSet set) {
        ensureGroundPlatform();
        VertexConsumer ground = bufferSource.getBuffer(set.ground());
        float breathe = 0.80f + 0.20f * Mth.sin(f * 0.08f);
        FxGeometry.groundQuad(poseStack, ground, R * PLATFORM_OUTER_SCALE, 0.02f, alpha * 0.20f * breathe);
        FxGeometry.groundQuad(poseStack, ground, R, 0.05f, alpha * 0.65f * breathe);
    }

    // 动态生成电路板平台纹理（渲染线程仅执行一次，512×512）：
    // 面板网格（细线每 1/8 + 粗线每 1/2）+ 格级随机断线（PCB 布线感）
    // + 少量面板淡填充 + 中心双环（对准中心八卦阵）+ 外缘径向淡出（藏掉方形 quad 四角）。
    private static void ensureGroundPlatform() {
        if (groundPlatformReady) return;
        groundPlatformReady = true;
        try {
            // 512 高分辨率：128px 纹理下「对称居中」最少占 2px（粗）、「1px 细线」必然
            // 偏移半像素——两者不可兼得；512 下对称双像素的世界宽度 = 128 版的 1px 线，
            // 居中与纤细同时满足。世界尺度特征（格/细线）按 4 倍同步缩放保持不变。
            int size = 512;
            float half = size / 2f;
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x + 0.5f - half) / half;
                    float dz = (y + 0.5f - half) / half;
                    float dist = Mth.sqrt(dx * dx + dz * dz);
                    float intensity = 0f;
                    if (dist <= 1f) {
                        // 世界尺度与 128px 版对齐：格 16px@128 = 64px@512
                        int cellX = x / 64;
                        int cellY = y / 64;
                        // 少数格子铺一层极淡板面（面板块）
                        if (hash2f(cellX * 3 + 211, cellY * 5 + 37) < 0.16f) {
                            intensity = Math.max(intensity, 0.04f);
                        }
                        // 细网格线（每 16px），按格随机断线 → 布过线的电路板
                        boolean minorX = x % 64 == 0 && hash2f(cellX, cellY) > 0.30f;
                        boolean minorZ = y % 64 == 0 && hash2f(cellX + 101, cellY + 17) > 0.30f;
                        if (minorX || minorZ) intensity = Math.max(intensity, 0.12f);
                        // 主干十字线（不断线）：255/256 双像素关于纹理几何中心（256.0）对称，
                        // 交点精确 = 领域核心（单像素线中心 0.5px 偏移曾被实测观察到，不可用）；
                        // 512 分辨率下双像素的世界宽度 = 128px 版单像素线，居中纤细兼得。
                        if (x == 255 || x == 256 || y == 255 || y == 256) {
                            intensity = Math.max(intensity, 0.26f);
                        }
                        // 中心双环（八卦阵半径 0.15R，双环扩大到其外围、留出空隙不重叠）
                        // 环项把采样坐标量化回旧 128 网格（每 4px 块取块中心评估）：旧版的
                        // 断续像素感来自 128 分辨率下环宽亚像素走样，用户指定保留该质感；
                        // 网格线/主干线仍用连续坐标，继续享受 512 高清。
                        float qx = (x >> 2) * 4f + 2f;
                        float qz = (y >> 2) * 4f + 2f;
                        float dxr = (qx - half) / half;
                        float dzr = (qz - half) / half;
                        intensity = Math.max(intensity, platformRing(dxr, dzr, 0f, 0f, 0.285f, 0.008f) * 0.40f);
                        intensity = Math.max(intensity, platformRing(dxr, dzr, 0f, 0f, 0.375f, 0.005f) * 0.25f);
                        // 外缘径向淡出
                        intensity *= 1f - Mth.clamp((dist - 0.86f) / 0.14f, 0f, 1f);
                    }
                    // 注意：通道值必须 ×255，intensity 是 0~1 归一化值
                    int r = FxGeometry.channel255(COL_MID[0] * 255f);
                    int g = FxGeometry.channel255(COL_MID[1] * 255f);
                    int b = FxGeometry.channel255(COL_MID[2] * 255f);
                    int a = FxGeometry.channel255(intensity * 255f);
                    // NativeImage 按 ABGR 打包
                    int rgba = (a << 24) | (b << 16) | (g << 8) | r;
                    img.setPixelRGBA(x, y, rgba);
                }
            }
            Minecraft.getInstance().getTextureManager().register(GROUND_TEX, new DynamicTexture(img));
        } catch (Exception e) {
            EndfieldSpellbook.LOGGER.error("[EndfieldSpellbook] Failed to generate gloompurge ground platform texture", e);
        }
    }

    /** 圆纹样强度：|到圆心距离 - 半径| 软阈值环。 */
    private static float platformRing(float dx, float dz, float cx, float cz, float r, float w) {
        float d = Math.abs(Mth.sqrt((dx - cx) * (dx - cx) + (dz - cz) * (dz - cz)) - r) / w;
        float v = Math.max(0f, 1f - d);
        return v * v;
    }

    // ===== 能量雾边界（单层流动呼吸雾幕）=====
    // 疏密形态来自稀疏条纹纹理（v4/u2 主条纹 + v3/u1 副条纹）；UV 的 v 随时间
    // 涌动（极慢匀速 + 呼吸摆动 → 雾在喘气而非匀速传送带），整体 alpha 极缓呼吸。
    // 高度剖面为长尾消散：雾核偏低、向上指数式衰减（0.65H 之后只剩微光长尾），
    // 拉高雾幕总高让尾巴延伸得更远 —— 视觉上没有可辨识的上边界，只剩流动条纹。

    private static final int   MIST_SEGMENTS = 32;
    private static final float MIST_HEIGHT_SCALE = 0.42f;
    private static final float MIST_ALPHA = 0.05f;
    private static final float MIST_FLOW_SPEED = 0.0025f; // UV v 涌动（极慢，配合摆动）
    private static final float MIST_SWAY = 0.06f;         // 涌动的呼吸摆动幅度（v 单位）

    // 高度剖面采样点（雾幕自身归一化高度）：高度 / alpha / 色调 三通道。
    // 三段色调 = 深蓝底 → 青雾核 → 白亮长尾（终末地雾的分层色彩）；长尾消散，无清晰上沿。
    private static final float[] MIST_VS = {0.05f, 0.35f, 0.65f, 0.88f, 1.00f};
    private static final float[] MIST_AS = {0.55f, 1.00f, 0.40f, 0.10f, 0f};
    private static final float[][] MIST_COLS = {
        COL_OUTER,                                    // 深蓝底
        COL_MID,                                      // 青雾核
        FxGeometry.mixCol(COL_MID, COL_SWEEP, 0.5f),  // 青偏白
        COL_SWEEP,                                    // 白亮长尾
        COL_SWEEP,
    };

    private void drawMistVeil(PoseStack pose, VertexConsumer c, float radius, float height,
                              float f, float alpha) {
        ensureMistTexture();
        var mat = pose.last();
        // 整体极缓呼吸（"场"的活性质感）
        float breathe = 0.65f + 0.35f * Mth.sin(f * 0.04f);
        // 条纹涌动：极慢匀速 + 呼吸摆动（打破匀速平移的传送带感）
        float flowV = f * MIST_FLOW_SPEED + MIST_SWAY * Mth.sin(f * 0.018f);
        float aBase = alpha * MIST_ALPHA * breathe;
        for (int s = 0; s < MIST_SEGMENTS; s++) {
            float th0 = Mth.TWO_PI * s / MIST_SEGMENTS;
            float th1 = Mth.TWO_PI * (s + 1) / MIST_SEGMENTS;
            float x0 = Mth.cos(th0) * radius, z0 = Mth.sin(th0) * radius;
            float x1 = Mth.cos(th1) * radius, z1 = Mth.sin(th1) * radius;
            // u：一整张纹理绕圆柱一圈（纹理内部自带 2 个条纹周期 → 接缝连续）
            float u0 = (float) s / MIST_SEGMENTS;
            float u1 = (float) (s + 1) / MIST_SEGMENTS;
            // 方位角错峰（关键：错的是几何高度而非浓度）——
            // 每个顶点独立采样噪声（3 周期绕圈 + 缓慢漂移）→ 相邻段共享边界值，
            // 波浪几何连续无阶梯；幅度收窄（0.65~0.95）→ 平缓起伏而非锯齿
            float hs0 = 0.65f + 0.30f * pNoise(th0 / Mth.TWO_PI * 3f, f * 0.006f, 3f, 71);
            float hs1 = 0.65f + 0.30f * pNoise(th1 / Mth.TWO_PI * 3f, f * 0.006f, 3f, 71);
            // 浓度错峰（另一次噪声，同样按顶点）：部分方位的雾更淡，"一圈"出现缺口
            float az0 = 0.35f + 0.65f * pNoise(th0 / Mth.TWO_PI * 4f + 11f, f * 0.005f, 4f, 131);
            float az1 = 0.35f + 0.65f * pNoise(th1 / Mth.TWO_PI * 4f + 11f, f * 0.005f, 4f, 131);
            // 沿高度剖面逐带展开：贴地雾核 → 长尾消散；顶点高度与纹理 v 同乘 hs
            // （雾高的地方条纹被拉长、雾矮的地方被压缩 → 浓淡与高度差更可信）
            for (int b = 0; b < MIST_VS.length - 1; b++) {
                float v0 = MIST_VS[b], v1 = MIST_VS[b + 1];
                float[] c0 = MIST_COLS[b], c1 = MIST_COLS[b + 1];
                // ⚠️ alpha 与颜色都按「高度剖面」取：同一高度的左右顶点同值，
                // 方位噪声只乘在各自角度上 → 段界两侧完全相等，竖直色带消失
                float aL0 = aBase * az0 * MIST_AS[b];
                float aR0 = aBase * az1 * MIST_AS[b];
                float aL1 = aBase * az0 * MIST_AS[b + 1];
                float aR1 = aBase * az1 * MIST_AS[b + 1];
                mistVertex(c, mat, x0, v0 * hs0 * height, z0, aL0, c0, u0, v0 * hs0 + flowV, th0);
                mistVertex(c, mat, x1, v0 * hs1 * height, z1, aR0, c0, u1, v0 * hs1 + flowV, th1);
                mistVertex(c, mat, x1, v1 * hs1 * height, z1, aR1, c1, u1, v1 * hs1 + flowV, th1);
                mistVertex(c, mat, x0, v1 * hs0 * height, z0, aL1, c1, u0, v1 * hs0 + flowV, th0);
            }
        }
    }

    /** 雾幕顶点：径向法线，UV 采样条纹纹理（明暗全在纹理灰度，浓度由顶点 alpha 承担）。 */
    private static void mistVertex(VertexConsumer c, PoseStack.Pose mat,
                                   float x, float y, float z, float a, float[] col,
                                   float u, float v, float th) {
        c.vertex(mat.pose(), x, y, z)
            .color(col[0], col[1], col[2], a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(mat.normal(), Mth.cos(th), 0f, Mth.sin(th))
            .endVertex();
    }

    // ===== 雾幕顶部蒸发 =====
    // 雾幕上缘不断"蒸发"出稀疏小光点：从雾幕内部升起、越过高处后消隐。
    // 用不规则的点云替换整齐的顶部渐变线 —— 终末地式的雾上缘处理，
    // 让上边界在视觉上彻底溶解为"雾在蒸发"。

    private static final int MIST_EVA_COUNT = 10;

    private void renderMistEvaporation(GloompurgeMarkEntity entity, float f, PoseStack poseStack,
                                       VertexConsumer glow, float alpha) {
        int seed = entity.getId();
        float R0 = entity.getRadius();
        float H = R0 * MIST_HEIGHT_SCALE;
        float breathe = 0.65f + 0.35f * Mth.sin(f * 0.04f);
        for (int i = 0; i < MIST_EVA_COUNT; i++) {
            float ang = hash01(seed, i * 7) * Mth.TWO_PI + f * 0.002f;   // 微弱周向漂移
            float frac = (hash01(seed, i * 7 + 1)
                + f * (0.004f + 0.003f * hash01(seed, i * 7 + 2))) % 1f;
            // 跟随所在方位的雾顶高度（与 drawMistVeil 同一噪声）→ 蒸发点贴着当地雾缘升起
            float hs = 0.65f + 0.30f * pNoise(ang / Mth.TWO_PI * 3f, f * 0.006f, 3f, 71);
            float y = H * hs * (0.5f + 0.75f * frac);
            float a = alpha * 0.10f * breathe * Mth.sin(frac * (float) Math.PI);
            // ⚠️ 不跳过低 alpha 顶点：保证被调用时恒有顶点（空 batch 铁律）
            float size = 0.06f + 0.10f * hash01(seed, i * 7 + 3);
            float rad = R0 * (0.99f + 0.03f * hash01(seed, i * 7 + 4));
            float cx = (float) Math.cos(ang) * rad;
            float cz = (float) Math.sin(ang) * rad;
            poseStack.pushPose();
            poseStack.translate(cx, y, cz);
            FxGeometry.billboardQuad(poseStack, glow, size, 0f, a, COL_MID);
            poseStack.popPose();
        }
    }

    // 动态生成能量流动纹理（渲染线程仅执行一次，64×64 灰度）：
    // 两层错频斜向条纹叠加（打破单一正弦的机械感），条纹稀疏化 + 竖向拉伸：
    // 主条纹 v2/u2 + 副条纹 v2/u1 反向斜切 —— u/v 均取整数周期：
    // ① 纹理 u=0/1 接缝连续；② u/v 方向均可无缝循环平移。
    private static void ensureMistTexture() {
        if (mistTextureReady) return;
        mistTextureReady = true;
        try {
            int size = 64;
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            for (int y = 0; y < size; y++) {
                float v = (float) y / (size - 1);
                for (int x = 0; x < size; x++) {
                    float u = (float) x / (size - 1);
                    // 主条纹 v2/u2 + 副条纹 v2/u1 反向斜切，竖向拉伸、加权叠加
                    float s1 = Mth.sin(v * Mth.TWO_PI * 2f + u * Mth.TWO_PI * 2f);
                    float s2 = Mth.sin(v * Mth.TWO_PI * 2f - u * Mth.TWO_PI * 1f + 1.7f);
                    float stripe = (0.5f + 0.5f * s1) * 0.6f + (0.5f + 0.5f * s2) * 0.4f;
                    // 平方加深对比：暗纹近乎全透、亮纹成流
                    float intensity = 0.10f + 0.90f * stripe * stripe;
                    int c = FxGeometry.channel255(intensity * 255f);
                    // NativeImage 按 ABGR 打包；alpha 给满，明暗全靠灰度（顶点色上青）
                    int rgba = (255 << 24) | (c << 16) | (c << 8) | c;
                    img.setPixelRGBA(x, y, rgba);
                }
            }
            Minecraft.getInstance().getTextureManager().register(MIST_TEX, new DynamicTexture(img));
        } catch (Exception e) {
            EndfieldSpellbook.LOGGER.error("[EndfieldSpellbook] Failed to generate gloompurge mist flow texture", e);
        }
    }

    /** 周期性 value noise（u 方向按 period 取模 → 绕圈接缝连续），返回 0~1。 */
    private static float pNoise(float u, float v, float period, int seed) {
        int ip = (int) period;
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        float xf = u - x0;
        float yf = v - y0;
        float sx = xf * xf * (3f - 2f * xf);
        float sy = yf * yf * (3f - 2f * yf);
        int xm = ((x0 % ip) + ip) % ip;
        int xp = (xm + 1) % ip;
        float n00 = hash2f(seed + xm, y0);
        float n10 = hash2f(seed + xp, y0);
        float n01 = hash2f(seed + xm, y0 + 1);
        float n11 = hash2f(seed + xp, y0 + 1);
        float a = n00 + (n10 - n00) * sx;
        float b = n01 + (n11 - n01) * sx;
        return a + (b - a) * sy;
    }

    // ===== 线框全息楼阁塔（终末地构图主体）=====
    // 方形多层楼阁：台基 → N 层塔身（角柱 + 上下梁环 + 中腰线）→ 飞檐 → 塔刹十字。
    // 全部走 cylEdge 薄片线（径向法线 → 塔身任何面正/侧看都有宽度），
    // 建造进度 build 自下而上扫描点亮（materialization），前沿处另有一圈白青扫描环。

    private void renderTowers(GloompurgeMarkEntity entity, float f, PoseStack poseStack,
                              VertexConsumer glow, float alpha) {
        int seed = entity.getId();
        float R0 = entity.getRadius();
        for (int i = 0; i < TOWER_COUNT; i++) {
            // 逐塔错峰动工；塔位/塔高不随地面展开移动 —— 在最终位置原地生长
            float build = Mth.clamp(
                (f - TOWER_GROW_DELAY - i * TOWER_STAGGER) / TOWER_BUILD_TICKS, 0f, 1f);
            if (build <= 0.002f) continue;
            Vec3 top = towerTop(seed, i, R0);
            float tx = (float) top.x;
            float tz = (float) top.z;
            float H = (float) top.y;
            float W = Math.max(1.2f, H * 0.16f);
            int tiers = 4 + (int) (hash01(seed, i + 48) * 1.99f);   // 4~5 层，天际线错落
            // 门朝核心：面法线对齐径向（方位角 mod 90°）+ ±12° 哈希微差 → 四方仪仗向心仪仗
            float rot = towerAngle(seed, i) + (hash01(seed, i + 64) - 0.5f) * 24f;
            float aTower = alpha * (0.80f + 0.20f * hash01(seed, i + 80));
            drawTower(poseStack, glow, f, tx, tz, rot, H, W, tiers, build, aTower);
        }
    }

    /** 塔 i 的方位角（塔位与朝向共用，保证两者一致）。 */
    private static float towerAngle(int seed, int i) {
        return i * 360f / TOWER_COUNT + (hash01(seed, i) - 0.5f) * 16f;
    }

    /** 塔 i 的布局（世界局部坐标 + 塔高），塔阵与光块散布共用。
     *  4 座塔均匀坐落在地面 UI 环边界（dist = 领域半径），角度仅 ±8° 哈希微偏。 */
    private static Vec3 towerTop(int seed, int i, float R0) {
        float angDeg = towerAngle(seed, i);
        return new Vec3(
            Math.cos(Math.toRadians(angDeg)) * R0,
            R0 * TOWER_HEIGHT_SCALE * (0.82f + hash01(seed, i + 32) * 0.18f),
            Math.sin(Math.toRadians(angDeg)) * R0);
    }

    /** 建造门槛：yN = 归一化高度，build 扫过该高度才点亮（软过渡 0.10）。 */
    private static float litAt(float build, float y, float H) {
        return Mth.clamp((build - y / H) / 0.10f, 0f, 1f);
    }

    private void drawTower(PoseStack pose, VertexConsumer c, float f,
                           float tx, float tz, float rotDeg, float H, float W, int tiers,
                           float build, float alpha) {
        float cs = Mth.cos(rotDeg * ((float) Math.PI / 180f));
        float sn = Mth.sin(rotDeg * ((float) Math.PI / 180f));
        float roofH = H * 0.05f;
        float spireH = H * 0.06f;
        float bodyH = (H - roofH * tiers - spireH) / tiers;
        float wBase = W * 1.30f;
        float width = Math.max(0.035f, W * 0.028f);

        // 台基：双层扁框 + 四角短柱
        towerRing(pose, c, cs, sn, tx, tz, H, build, 0.03f, wBase, width, alpha * 0.9f, COL_MID);
        towerRing(pose, c, cs, sn, tx, tz, H, build, 0.12f, wBase * 0.92f, width, alpha * 0.9f, COL_MID);
        for (int k = 0; k < 4; k++) {
            float px = (k == 0 || k == 3) ? -wBase : wBase;
            float pz = (k < 2) ? -wBase : wBase;
            towerPost(pose, c, cs, sn, tx, tz, H, build, px, pz, 0.03f, 0.12f, width, alpha * 0.9f, COL_MID);
        }

        // 塔身逐层：角柱 + 上梁环（亮）+ 下梁环（暗）+ 中腰线（发丝）+ 飞檐
        float y = 0.12f;
        for (int t = 0; t < tiers; t++) {
            float w = W * (1f - 0.13f * t);
            float y0 = y;
            float y1 = y + bodyH;
            for (int k = 0; k < 4; k++) {
                float px = (k == 0 || k == 3) ? -w : w;
                float pz = (k < 2) ? -w : w;
                towerPost(pose, c, cs, sn, tx, tz, H, build, px, pz, y0, y1, width, alpha, COL_MID);
            }
            towerRing(pose, c, cs, sn, tx, tz, H, build, y1, w, width, alpha, COL_MID);
            towerRing(pose, c, cs, sn, tx, tz, H, build, y0, w, width, alpha * 0.55f, COL_DEEP);
            towerRing(pose, c, cs, sn, tx, tz, H, build, y0 + bodyH * 0.5f, w * 1.02f, width * 0.5f, alpha * 0.4f, COL_OUTER);
            // 飞檐：出挑环（角部抬高的方环）+ 戗脊（塔身四角 → 檐角）+ 檐下暗环
            float wr = w * 1.34f;
            float yr = y1 + roofH;
            float lift = roofH * 0.55f;
            float lit = litAt(build, (y1 + yr) * 0.5f, H);
            if (lit > 0.002f) {
                float aEave = alpha * lit;
                // 檐口外环（四角抬高 → 每边是两端同高的微斜线，look 上翘）
                for (int k = 0; k < 4; k++) {
                    float x0 = (k == 0 || k == 3) ? -wr : wr;
                    float z0 = (k < 2) ? -wr : wr;
                    float x1 = (k == 0 || k == 1) ? -wr : wr;
                    float z1 = (k == 0 || k == 1) ? -wr : wr;
                    towerEdge(pose, c, cs, sn, tx, tz,
                        x0, yr + lift, z0, x1, yr + lift, z1, width, aEave, COL_INNER);
                    // 戗脊：塔身角 → 檐角
                    float bx = (k == 0 || k == 3) ? -w : w;
                    float bz = (k < 2) ? -w : w;
                    towerEdge(pose, c, cs, sn, tx, tz,
                        bx, y1, bz, x1, yr + lift, z1, width * 0.8f, aEave * 0.8f, COL_MID);
                }
                towerRing(pose, c, cs, sn, tx, tz, H, build, yr, wr * 0.99f, width * 0.5f, alpha * 0.4f, COL_OUTER);
            }
            y = yr;
        }

        // 塔刹：中心竖杆 + 十字宝顶（终末地塔顶的十字架轮廓）+ 顶珠方框
        float yTop = y;
        float litSpire = litAt(build, yTop + spireH * 0.5f, H);
        if (litSpire > 0.002f) {
            float aSpire = alpha * litSpire;
            float crossW = W * 0.42f;
            float crossY = yTop + spireH * 0.55f;
            towerEdge(pose, c, cs, sn, tx, tz, 0f, yTop, 0f, 0f, yTop + spireH, 0f, width, aSpire, COL_MID);
            towerEdge(pose, c, cs, sn, tx, tz, -crossW, crossY, 0f, crossW, crossY, 0f, width, aSpire, COL_INNER);
            towerEdge(pose, c, cs, sn, tx, tz, 0f, crossY, -crossW, 0f, crossY, crossW, width, aSpire, COL_INNER);
            float aTop = alpha * litSpire * (0.7f + 0.3f * Mth.sin(f * 0.3f + tx));
            towerRing(pose, c, cs, sn, tx, tz, H, build, yTop + spireH, W * 0.10f, width * 0.8f, aTop, COL_SWEEP);
        }

        // 建造扫描环：build 前沿处一圈白青方环（materialization 高光，建成即消失）
        if (build < 1f) {
            float yF = build * H;
            float scanA = Mth.sin(build * (float) Math.PI) * 0.9f;
            towerRing(pose, c, cs, sn, tx, tz, H, build + 0.10f, yF, W * 1.05f, width * 1.4f, alpha * scanA, COL_SWEEP);
        }
    }

    /** 塔局部水平方框环（y 高度、半宽 w、4 边）。 */
    private void towerRing(PoseStack pose, VertexConsumer c, float cs, float sn,
                           float tx, float tz, float H, float build,
                           float y, float w, float width, float a, float[] col) {
        float lit = litAt(build, y, H);
        if (lit <= 0.002f) return;
        a *= lit;
        towerEdge(pose, c, cs, sn, tx, tz, -w, y, -w, w, y, -w, width, a, col);
        towerEdge(pose, c, cs, sn, tx, tz, w, y, -w, w, y, w, width, a, col);
        towerEdge(pose, c, cs, sn, tx, tz, w, y, w, -w, y, w, width, a, col);
        towerEdge(pose, c, cs, sn, tx, tz, -w, y, w, -w, y, -w, width, a, col);
    }

    /** 塔局部竖直柱：位于 (px, pz)。 */
    private void towerPost(PoseStack pose, VertexConsumer c, float cs, float sn,
                           float tx, float tz, float H, float build,
                           float px, float pz, float y0, float y1, float width, float a, float[] col) {
        float lit = litAt(build, (y0 + y1) * 0.5f, H);
        if (lit <= 0.002f) return;
        towerEdge(pose, c, cs, sn, tx, tz, px, y0, pz, px, y1, pz, width, a * lit, col);
    }

    /**
     * 塔局部线段 → 世界（绕塔轴旋转 + 平移），径向法线 → 宽度始终可见。
     * 中点在塔轴上时（塔刹竖杆/十字）回退为按线段方向取水平垂直法线。
     */
    private static void towerEdge(PoseStack pose, VertexConsumer c, float cs, float sn,
                                  float tx, float tz,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float width, float a, float[] col) {
        float wx0 = tx + ax * cs - az * sn;
        float wz0 = tz + ax * sn + az * cs;
        float wx1 = tx + bx * cs - bz * sn;
        float wz1 = tz + bx * sn + bz * cs;
        float mx = (ax + bx) * 0.5f;
        float mz = (az + bz) * 0.5f;
        float ml = Mth.sqrt(mx * mx + mz * mz);
        float nx, nz;
        if (ml > 1e-4f) {
            nx = mx / ml;
            nz = mz / ml;
        } else {
            // 轴心线段：法线取线段方向的水平垂直 → cross 后宽度竖直/水平皆可见
            nx = Math.abs(ax - bx) > Math.abs(az - bz) ? 0f : 1f;
            nz = nx == 0f ? 1f : 0f;
        }
        float nwx = nx * cs - nz * sn;
        float nwz = nx * sn + nz * cs;
        FxGeometry.cylEdge(pose, c, wx0, ay, wz0, wx1, by, wz1, width, a, col, col, nwx, nwz);
    }

    // ===== 上浮光块 =====
    // 终末地领域里缓慢上升的发光小方块（金/白青/青三色），billboard 亮核 + 光晕双层，
    // 位置/尺寸/速度由实体种子确定性散布；sine(frac·π) 让首尾自然淡入淡出。

    private void renderCubes(GloompurgeMarkEntity entity, float f, PoseStack poseStack,
                             VertexConsumer glow, float alpha) {
        float gate = Mth.clamp((f - CUBE_START_TICK) / 20f, 0f, 1f);
        if (gate <= 0.002f) return;
        int seed = entity.getId();
        float R0 = entity.getRadius();
        float maxH = R0 * TOWER_HEIGHT_SCALE;
        for (int i = 0; i < CUBE_COUNT; i++) {
            float ang = hash01(seed, i * 6) * Mth.TWO_PI;
            float rad = R0 * (0.12f + 0.80f * hash01(seed, i * 6 + 1));
            float size = 0.05f + 0.09f * hash01(seed, i * 6 + 2);
            float speed = 0.020f + 0.025f * hash01(seed, i * 6 + 3);   // 每 tick 上升相位
            float phase = hash01(seed, i * 6 + 4);
            float frac = (phase + f * speed) % 1f;
            float a = alpha * gate * Mth.sin(frac * (float) Math.PI) * 0.55f;
            // ⚠️ 不跳过低 alpha 顶点：保证被调用时恒有顶点（空 batch 铁律）
            float[] col = switch (i % 3) {
                case 0 -> COL_GOLD;    // 终末地浮块标志性的暖金
                case 1 -> COL_SWEEP;
                default -> COL_MID;
            };
            float cx = (float) Math.cos(ang) * rad;
            float cz = (float) Math.sin(ang) * rad;
            float y = 0.4f + frac * maxH;
            poseStack.pushPose();
            poseStack.translate(cx, y, cz);
            FxGeometry.billboardQuad(poseStack, glow, size * 2.1f, 0f, a * 0.35f, col); // 光晕
            FxGeometry.billboardQuad(poseStack, glow, size, 0.01f, a, col);              // 亮核
            poseStack.popPose();
        }
    }

    // 八卦阵：外环 + 内环 + 后天八卦卦象，旋转减半，径向渐变

    private void renderBagua(GloompurgeMarkEntity entity, float f, PoseStack poseStack,
                             MultiBufferSource bufferSource, float alpha, RenderTypeSet set) {
        float rot = entity.getRot() + f * 0.75f;
        float radius = entity.getRadius();
        float innerR = radius * BAGUA_INNER_SCALE;
        VertexConsumer consumer = bufferSource.getBuffer(set.mark());
        FxGeometry.polygon(poseStack, consumer, 8, radius, rot, Y_OFFSET, 0.12f, alpha, COL_MID);   // 外环
        FxGeometry.polygon(poseStack, consumer, 8, innerR, rot, Y_OFFSET, 0.08f, alpha, COL_INNER); // 内环

        // 后天八卦：离坤兑乾坎艮震巽，每卦三爻（阳爻实线、阴爻断线），径向叠放于内外环之间
        float rMid = radius * 0.775f;
        float yaoLength = radius * 0.22f;
        float yaoSpacing = radius * 0.06f;
        float yaoGap = radius * 0.035f;
        float yaoWidth = Math.max(0.06f, radius * 0.015f);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(rot + i * 45f);
            float ux = (float) Math.cos(a);
            float uz = (float) Math.sin(a);
            // 切向（爻线方向，垂直于径向）
            float tx = -uz;
            float tz = ux;
            int bits = TRIGRAM_ORDER[i];
            // 每卦角度 shimmer，全息薄膜感
            float shimmer = 0.8f + 0.2f * Mth.sin((float) a * 2f + f * 0.2f);
            for (int k = 0; k < 3; k++) {
                float rad = rMid + (k - 1) * yaoSpacing;
                float cx = ux * rad;
                float cz = uz * rad;
                float half = yaoLength * 0.5f;
                // 爻色自内而外：白青 → 青 渐变
                float[] col = FxGeometry.mixCol(COL_INNER, COL_MID, k / 2f);
                if ((bits >> k & 1) == 1) {
                    // 阳爻：整条实线
                    FxGeometry.edge(poseStack, consumer,
                        cx - tx * half, Y_OFFSET, cz - tz * half,
                        cx + tx * half, Y_OFFSET, cz + tz * half, yaoWidth, alpha * shimmer, col, col);
                } else {
                    // 阴爻：中段留缺的断线
                    float g = yaoGap * 0.5f;
                    FxGeometry.edge(poseStack, consumer,
                        cx - tx * half, Y_OFFSET, cz - tz * half,
                        cx - tx * g, Y_OFFSET, cz - tz * g, yaoWidth, alpha * shimmer, col, col);
                    FxGeometry.edge(poseStack, consumer,
                        cx + tx * g, Y_OFFSET, cz + tz * g,
                        cx + tx * half, Y_OFFSET, cz + tz * half, yaoWidth, alpha * shimmer, col, col);
                }
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GloompurgeMarkEntity entity) {
        return TEXTURE;
    }
}

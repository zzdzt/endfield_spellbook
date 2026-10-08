package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 输出到自研 FBO（fxFBO）的 RenderType 工厂 —— 破晦阵后处理管线专用。
 *
 * 与 vanilla 特效 RenderType 的关键差异：
 *   - 输出目标：普通后处理特效写 fxFBO 的 CA0；焚灭火环独占 CA1，作为独立
 *     screen-space 扭曲输入，之后再合入 CA0。
 *     setup 时动态绑定（每帧 fboId 可能因 resize 变化，不能缓存）；
 *   - 深度：fxFBO 深度 = 每帧从主 RT blit 拷贝的真实场景深度 → LEQUAL 测试
 *     获得正确的世界遮挡；深度不可用（格式不兼容/拷贝失败）时切换 NO_DEPTH_TEST
 *     变体（特效恒可见，失去遮挡，可接受降级）——因此每个类型有 depth/nepth 双常量；
 *   - 混合：加算（合成在自家 FBO 内闭环，不再受光影包覆盖）；
 *   - 不写深度（COLOR_WRITE），特效之间按提交顺序叠加。
 *
 * shader 沿用 RENDERTYPE_ENTITY_TRANSLUCENT_SHADER（NEW_ENTITY 顶点格式），
 * FxGeometry 全部顶点辅助零改动即可复用。
 */
public final class PostRenderTypes extends RenderStateShard {

    /** 仅为满足 RenderStateShard 构造约束（shard 状态全部为静态常量，无实例状态）。 */
    private PostRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /** 输出目标：绑定 fxFBO；teardown 绑回主 RenderTarget。 */
    private static final OutputStateShard FX_OUTPUT = new OutputStateShard(
        "endfield_fx_target_ca0",
        () -> {
            ArcaneFBO fx = PipelinePost.fxBuffer();
            if (fx != null && fx.frameBufferId() != -1) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fx.frameBufferId());
                GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            }
        },
        () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(true)
    );

    /** 火环三材质分别写 CA1/CA2/CA3，后处理阶段再统一组合。 */
    private static final OutputStateShard FLAME_OUTER_OUTPUT = flameOutput("endfield_fx_target_ca1_flame_outer", GL30.GL_COLOR_ATTACHMENT1);
    private static final OutputStateShard FLAME_BODY_OUTPUT  = flameOutput("endfield_fx_target_ca2_flame_body",  GL30.GL_COLOR_ATTACHMENT2);
    private static final OutputStateShard FLAME_CORE_OUTPUT  = flameOutput("endfield_fx_target_ca3_flame_core",  GL30.GL_COLOR_ATTACHMENT3);

    private static OutputStateShard flameOutput(String name, int attachment) {
        return new OutputStateShard(
            name,
            () -> {
                ArcaneFBO fx = PipelinePost.fxBuffer();
                if (fx != null && fx.frameBufferId() != -1) {
                    GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fx.frameBufferId());
                    GL20.glDrawBuffers(new int[]{attachment});
                }
            },
            () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(true)
        );
    }

    private static RenderType postAdditive(String name, ResourceLocation texture,
                                           boolean linearFilter, boolean depthTest) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, true, RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, linearFilter, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(depthTest ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setOutputState(FX_OUTPUT)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true));
    }

    private static RenderType postAdditiveToFlame(String name, ResourceLocation texture,
                                                    boolean linearFilter, boolean depthTest,
                                                    OutputStateShard output) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, true, RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, linearFilter, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(depthTest ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setOutputState(output)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true));
    }

    public record FlameRingRenderTypeSet(RenderType outer, RenderType body, RenderType core) {
    }

    private static final ResourceLocation FLAME_RING = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "textures/vfx/fire_ring.png");

    private static final FlameRingRenderTypeSet FLAME_DEPTH_SET = new FlameRingRenderTypeSet(
        postAdditiveToFlame("flame_ring_post_outer", FLAME_RING, true, true, FLAME_OUTER_OUTPUT),
        postAdditiveToFlame("flame_ring_post_body",  FLAME_RING, true, true, FLAME_BODY_OUTPUT),
        postAdditiveToFlame("flame_ring_post_core",  FLAME_RING, true, true, FLAME_CORE_OUTPUT));

    private static final FlameRingRenderTypeSet FLAME_NO_DEPTH_SET = new FlameRingRenderTypeSet(
        postAdditiveToFlame("flame_ring_post_outer_nd", FLAME_RING, true, false, FLAME_OUTER_OUTPUT),
        postAdditiveToFlame("flame_ring_post_body_nd",  FLAME_RING, true, false, FLAME_BODY_OUTPUT),
        postAdditiveToFlame("flame_ring_post_core_nd",  FLAME_RING, true, false, FLAME_CORE_OUTPUT));

    private static final GloompurgeMarkRenderer.RenderTypeSet DEPTH_SET =
        new GloompurgeMarkRenderer.RenderTypeSet(
            postAdditive("gloompurge_post_mark", GloompurgeTextures.MARK, false, true),
            postAdditive("gloompurge_post_ground", GloompurgeTextures.GROUND, true, true),
            postAdditive("gloompurge_post_mist", GloompurgeTextures.MIST, true, true),
            postAdditive("gloompurge_post_glow", GloompurgeTextures.GLOW, false, true));

    private static final GloompurgeMarkRenderer.RenderTypeSet NO_DEPTH_SET =
        new GloompurgeMarkRenderer.RenderTypeSet(
            postAdditive("gloompurge_post_mark_nd", GloompurgeTextures.MARK, false, false),
            postAdditive("gloompurge_post_ground_nd", GloompurgeTextures.GROUND, true, false),
            postAdditive("gloompurge_post_mist_nd", GloompurgeTextures.MIST, true, false),
            postAdditive("gloompurge_post_glow_nd", GloompurgeTextures.GLOW, false, false));

    /** 深度可用 → LEQUAL 遮挡版；不可用 → 恒可见降级版。 */
    public static GloompurgeMarkRenderer.RenderTypeSet set(boolean depthReady) {
        return depthReady ? DEPTH_SET : NO_DEPTH_SET;
    }

    /** 火环专用输出：CA1 只存火环，便于单独做屏幕空间扰动。 */
    public static FlameRingRenderTypeSet setFlameRing(boolean depthReady) {
        return depthReady ? FLAME_DEPTH_SET : FLAME_NO_DEPTH_SET;
    }

    /** 破晦阵四条流的纹理路径（与 GloompurgeMarkRenderer 中的一致）。 */
    static final class GloompurgeTextures {
        static final ResourceLocation MARK = ResourceLocation.fromNamespaceAndPath(
            EndfieldSpellbook.MOD_ID, "textures/entity/lizhi_yan/core.png");
        static final ResourceLocation GROUND = ResourceLocation.fromNamespaceAndPath(
            EndfieldSpellbook.MOD_ID, "dynamic/gloompurge_ground_platform");
        static final ResourceLocation MIST = ResourceLocation.fromNamespaceAndPath(
            EndfieldSpellbook.MOD_ID, "dynamic/gloompurge_mist_flow");
        static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            EndfieldSpellbook.MOD_ID, "textures/entity/soul_orb/white.png");

        private GloompurgeTextures() {
        }
    }
}

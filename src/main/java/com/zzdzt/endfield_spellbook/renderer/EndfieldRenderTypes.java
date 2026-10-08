package com.zzdzt.endfield_spellbook.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Endfield Spellbook 自定义 RenderType 工厂。
 *
 * 设计（参数化，移植自 ArcaneMag）：
 * - entity 系工厂统一走私有 {@link #entityFx}，混合/过滤/深度/输出目标按参数装配；
 * - 所有加算特效默认输出 {@link RenderStateShard#PARTICLES_TARGET}（借鉴梦幻终焉 mhzy）：
 *   开光影（Oculus/Iris）时该层由光影管线处理 → 免费 bloom 泛光；
 *   不开光影、非"极佳"图形时 vanilla 行为不变（shard setup 为空操作），无副作用；
 * - 正常 alpha 混合层（需要真正"变暗"的几何，如灵魂球黑核）不进粒子层，保持主缓冲。
 *
 * 调用方约定：RenderType 一律在 renderer 的 static final 常量中缓存，
 * 不要在 render() 内调用工厂（RenderType.create 每次都产生新实例）。
 */
public final class EndfieldRenderTypes extends RenderStateShard {

    private EndfieldRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /** 混合模式：加算（发光）或正常 alpha（可变暗/遮挡） */
    private enum FxBlend { ADDITIVE, ALPHA }

    /**
     * entity 系特效 RenderType 统一装配。
     *
     * @param linearFilter true=线性过滤（渐变纹理必需，NEAREST 会采出台阶）
     * @param blend        加算 / 正常混合
     * @param writeDepth   true=写深度（需要遮挡关系，如能量柱）；false=仅写颜色（前后表面叠加，撑"场"感）
     * @param ignoreDepth  true=完全不测深度（必须覆盖一切之上的特效，如护盾格挡）
     * @param particles    true=输出到粒子帧缓冲（开光影免费 bloom；加算层恒 true，ALPHA 层恒 false）
     */
    private static RenderType entityFx(String name, ResourceLocation texture,
                                       boolean linearFilter, FxBlend blend,
                                       boolean writeDepth, boolean ignoreDepth, boolean particles) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(texture, linearFilter, false))
            .setTransparencyState(blend == FxBlend.ADDITIVE ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL);
        if (ignoreDepth) {
            builder.setDepthTestState(NO_DEPTH_TEST);
        }
        builder.setWriteMaskState(writeDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE);
        if (particles) {
            builder.setOutputState(PARTICLES_TARGET);
        }
        builder.setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY);
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, true, builder.createCompositeState(true));
    }

    /**
     * 加算合成 + 无背面剔除 + 写深度 + 粒子帧缓冲。
     * 调用方需在 vertex 中传入 LightTexture.FULL_BRIGHT 以实现自发光。
     */
    public static RenderType entityAdditiveGlowNoCull(String name, ResourceLocation texture) {
        return entityFx(name, texture, false, FxBlend.ADDITIVE, true, false, true);
    }

    /**
     * 正常 alpha 混合 + 无背面剔除 + 写深度。
     *
     * 加算混合下深色几何体对屏幕零贡献（黑色即完全不可见，且无法遮挡背景），
     * 凡是需要真正"变暗"的部分（如灵魂球的黑色内核）必须走这个类型。
     * 深度写入让内核遮挡其后方的加算层，形成实心球体感而非透明重影。
     */
    public static RenderType entityAlphaNoCull(String name, ResourceLocation texture) {
        return entityFx(name, texture, false, FxBlend.ALPHA, true, false, false);
    }

    /**
     * {@link #entityAdditiveGlowNoCull} 的线性过滤版（渐变纹理用，
     * 如破晦阵边界光墙）。
     */
    public static RenderType entityAdditiveGlowNoCullLinear(String name, ResourceLocation texture) {
        return entityFx(name, texture, true, FxBlend.ADDITIVE, true, false, true);
    }

    /**
     * 加算 + 无背面剔除 + 仅写颜色（不写深度）。
     * 同一特效内的多个加算层不会因深度写入互相覆盖，保留深度测试确保墙壁遮挡生效。
     */
    public static RenderType entityAdditiveGlowNoCullColorOnly(String name, ResourceLocation texture) {
        return entityFx(name, texture, false, FxBlend.ADDITIVE, false, false, true);
    }

    /**
     * 线性过滤加算 + 可选深度写入 + 粒子帧缓冲（破晦阵能量膜/能量柱用）。
     *
     * @param writeDepth true=写深度（能量柱等需要遮挡关系）；false=仅写颜色（能量膜
     *                   需要前后表面都参与叠加，撑出"场"的包裹感）
     */
    public static RenderType entityAdditiveGlowNoCullLinearParticles(
        String name, ResourceLocation texture, boolean writeDepth) {
        return entityFx(name, texture, true, FxBlend.ADDITIVE, writeDepth, false, true);
    }

    /**
     * 纯白纹理 + 加算 + 仅写颜色 + 粒子帧缓冲。
     * 颜色完全由顶点 color 决定（采样任何 uv 都是白），适合需要精确发色的叠加光效
     * （辉光、符纹亮核、数据流）。纹理用 textures/entity/soul_orb/white.png。
     */
    public static RenderType entityAdditiveGlowNoCullParticlesColorOnly(
        String name, ResourceLocation texture) {
        return entityFx(name, texture, false, FxBlend.ADDITIVE, false, false, true);
    }

    /**
     * 自定义 core shader + 加算 + 无背面剔除 + 粒子帧缓冲。
     *
     * 与 entity 系的本质区别：片元着色器是自定义程序（可采样纹理，也可纯程序化生成图案），
     * 时间由 Time uniform 驱动。顶点格式 POSITION_COLOR_TEX：只需 .vertex/.color/.uv。
     *
     * @param writeDepth true = 写深度（能量柱等需要遮挡关系）；false = 仅写颜色
     *                   （能量膜/辉光层需要前后表面都参与叠加，撑出"场"的包裹感）
     * ⚠️ 调用前必须确认 shader 已注册成功，否则 setShader(null) 会导致绘制崩溃。
     */
    public static RenderType shaderAdditive(String name, ResourceLocation bindTexture,
                                            java.util.function.Supplier<net.minecraft.client.renderer.ShaderInstance> shader,
                                            boolean writeDepth) {
        return shaderAdditive(name, bindTexture, shader, writeDepth, false);
    }

    /**
     * {@link #shaderAdditive} 完整版。
     *
     * @param linearFilter true = 线性过滤。⚠️ shader **会采样纹理**时必须为 true
     *                     （渐变纹理用 NEAREST 会采出色带）；纯程序化 shader（纹理只是占位）用 false。
     */
    public static RenderType shaderAdditive(String name, ResourceLocation bindTexture,
                                            java.util.function.Supplier<net.minecraft.client.renderer.ShaderInstance> shader,
                                            boolean writeDepth, boolean linearFilter) {
        return RenderType.create(
            name,
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            1024,
            true,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                .setTextureState(new RenderStateShard.TextureStateShard(bindTexture, linearFilter, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(writeDepth
                    ? RenderStateShard.COLOR_DEPTH_WRITE
                    : RenderStateShard.COLOR_WRITE)
                .setOutputState(RenderStateShard.PARTICLES_TARGET)
                .createCompositeState(true)
        );
    }
}

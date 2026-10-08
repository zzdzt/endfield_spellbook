package com.zzdzt.endfield_spellbook.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Endfield Spellbook 客户端配置（Type.CLIENT）。
 *
 * ⚠️ 只用 ForgeConfigSpec API，不引用 net.minecraft.client.* —— 主类在服务端也会
 * 加载本类（registerConfig 需要 SPEC 常量），保持纯净才不会触发服务端加载 client 类。
 * 实际消费方是 client-only 的 {@code com.zzdzt.endfield_spellbook.client.fx.FxLod}。
 */
public class ClientConfig {

    public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ForgeConfigSpec.BooleanValue POST_PIPELINE;
    public static final ForgeConfigSpec.DoubleValue POST_BLOOM_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue POST_BRIGHTNESS;

    static {
        CLIENT_BUILDER.push("vfx");
        VFX_QUALITY = CLIENT_BUILDER
            .comment("Visual effect quality tier (affects spell visuals only, no gameplay impact).",
                     "HIGH   = all layers, full tessellation (default; identical to pre-LOD appearance)",
                     "MEDIUM = beyond 32 blocks: drop glow layers, halve membrane segments;",
                     "         beyond 56 blocks: halve energy pillars",
                     "LOW    = drop glow layers entirely; membrane at half segments;",
                     "         pillars at 1/3 beyond 24 blocks")
            .defineEnum("quality", VfxQuality.HIGH);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.push("post");
        POST_PIPELINE = CLIENT_BUILDER
            .comment("Self-managed post-processing pipeline for Gloompurge domain effects.",
                     "Renders spell FX into an off-screen FBO with own bloom, then composites",
                     "onto the main framebuffer — shaderpack independent (Iris/Oculus safe).",
                     "When disabled, effects fall back to vanilla PARTICLES_TARGET / safe path.")
            .define("pipeline", true);
        POST_BLOOM_STRENGTH = CLIENT_BUILDER
            .comment("Bloom intensity of the post pipeline (only when quality != LOW).",
                     "0.0 = no bloom; 1.0 = default; up to 3.0 = heavy glow.")
            .defineInRange("bloomStrength", 0.8, 0.0, 3.0);
        POST_BRIGHTNESS = CLIENT_BUILDER
            .comment("Master brightness of spell FX in the post pipeline (fx + bloom are both",
                     "scaled). Lower this if the domain looks overexposed (fx and its bloom overlap).",
                     "0.7 = default; 1.0 = additive-at-full; 0.5 = subtle.")
            .defineInRange("brightness", 0.7, 0.0, 2.0);
        CLIENT_BUILDER.pop();

        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}

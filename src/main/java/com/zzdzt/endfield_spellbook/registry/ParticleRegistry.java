package com.zzdzt.endfield_spellbook.registry;

import com.mojang.serialization.Codec;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.particle.InkZapParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ParticleRegistry {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, EndfieldSpellbook.MOD_ID);

    public static final RegistryObject<ParticleType<InkZapParticleOption>> INK_ZAP_PARTICLE =
        PARTICLES.register("ink_zap", () -> new ParticleType<InkZapParticleOption>(false, InkZapParticleOption.DESERIALIZER) {
            @Override
            public Codec<InkZapParticleOption> codec() {
                return InkZapParticleOption.CODEC;
            }
        });

    /** 血萤（驱火焚影 V1 视觉主体）：overrideLimiter=true，最低画质仍显示——血翼整体不可见则机制失读。 */
    public static final RegistryObject<SimpleParticleType> BLOOD_FIREFLY =
        PARTICLES.register("blood_firefly", () -> new SimpleParticleType(true));

    // ========== 血雾三档（血翼体积层，装饰粒子 overrideLimiter=false 低画质可裁剪） ==========

    public static final RegistryObject<SimpleParticleType> BLOOD_MIST_CORE =
        PARTICLES.register("blood_mist_core", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> BLOOD_MIST_OUTER =
        PARTICLES.register("blood_mist_outer", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> BLOOD_MIST_TRAIL =
        PARTICLES.register("blood_mist_trail", () -> new SimpleParticleType(false));

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}

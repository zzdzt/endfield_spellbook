package com.zzdzt.endfield_spellbook.registry;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.effect.ArtsVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.CombustionEffect;
import com.zzdzt.endfield_spellbook.effect.ErodedEffect;
import com.zzdzt.endfield_spellbook.effect.FrozenEffect;
import com.zzdzt.endfield_spellbook.effect.HeatVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.LiquidNitrogenMarkedEffect;
import com.zzdzt.endfield_spellbook.effect.MoltenFireMarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EffectRegistry {
    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EndfieldSpellbook.MOD_ID);

    public static final RegistryObject<MobEffect> LIQUID_NITROGEN_MARKED =
        EFFECTS.register("liquid_nitrogen_marked", LiquidNitrogenMarkedEffect::new);

    // ========== 元素异常（燃烧/导电/腐蚀） ==========

    public static final RegistryObject<MobEffect> COMBUSTION =
        EFFECTS.register("combustion", CombustionEffect::new);

    public static final RegistryObject<MobEffect> ARTS_VULNERABLE =
        EFFECTS.register("arts_vulnerable", ArtsVulnerableEffect::new);

    public static final RegistryObject<MobEffect> ERODED =
        EFFECTS.register("eroded", ErodedEffect::new);

    public static final RegistryObject<MobEffect> FROZEN_MARK =
        EFFECTS.register("frozen_mark", FrozenEffect::new);

    public static final RegistryObject<MobEffect> MOLTEN_FIRE_MARK =
        EFFECTS.register("molten_fire_mark", MoltenFireMarkEffect::new);

    public static final RegistryObject<MobEffect> HEAT_VULNERABLE =
        EFFECTS.register("heat_vulnerable", HeatVulnerableEffect::new);

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}

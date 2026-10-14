package com.zzdzt.endfield_spellbook.registry;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.effect.ArtsVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.CombustionEffect;
import com.zzdzt.endfield_spellbook.effect.ErodedEffect;
import com.zzdzt.endfield_spellbook.effect.FocusedEffect;
import com.zzdzt.endfield_spellbook.effect.FrozenEffect;
import com.zzdzt.endfield_spellbook.effect.HeatVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.LiquidNitrogenMarkedEffect;
import com.zzdzt.endfield_spellbook.effect.MoltenFireMarkEffect;
import com.zzdzt.endfield_spellbook.effect.WindArrowFloatEffect;
import com.zzdzt.endfield_spellbook.spell.tidalwave.TidalWaveDashEffect;
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

    /** 风矢穿林的零重力浮空；生命周期由真实 Recast 控制。 */
    public static final RegistryObject<MobEffect> WIND_ARROW_FLOAT =
        EFFECTS.register("wind_arrow_float", WindArrowFloatEffect::new);

    // ========== 指定研究对象 ==========
    /** 聚焦 HUD 标记（纯显示，施加/移除由 FocusMarkEntity 托管）。 */
    public static final RegistryObject<MobEffect> FOCUSED =
        EFFECTS.register("focused", FocusedEffect::new);

    // ========== 踏潮卷浪 ==========
    /** 潮汐突进冲刺窗口（撞击/到期触发寒冷冲击；隐形效果）。 */
    public static final RegistryObject<MobEffect> TIDAL_WAVE_DASH =
        EFFECTS.register("tidal_wave_dash", TidalWaveDashEffect::new);

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}

package com.zzdzt.endfield_spellbook;

import com.mojang.logging.LogUtils;
import com.zzdzt.endfield_spellbook.config.ClientConfig;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.ParticleRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EndfieldSpellbook.MOD_ID)
public class EndfieldSpellbook {
    public static final String MOD_ID = "endfield_spellbook";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EndfieldSpellbook() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册客户端配置（特效质量/后处理管线开关）
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_SPEC);

        // 注册效果
        EffectRegistry.register(modEventBus);

        // 注册实体
        EntityRegistry.register(modEventBus);

        // 注册法术
        SpellRegistry.register(modEventBus);

        // 注册粒子
        ParticleRegistry.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void clientSetup(final FMLClientSetupEvent event) {
    }
}

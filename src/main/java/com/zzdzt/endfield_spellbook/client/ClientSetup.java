package com.zzdzt.endfield_spellbook.client;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.ElementRingRenderer;
import com.zzdzt.endfield_spellbook.element.InkWaterfallRenderer;
import com.zzdzt.endfield_spellbook.element.SunderbladeRenderer;
import com.zzdzt.endfield_spellbook.element.TargetMarkRenderer;
import com.zzdzt.endfield_spellbook.particle.BloodFireflyParticle;
import com.zzdzt.endfield_spellbook.particle.InkZapParticle;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.ParticleRegistry;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkRenderer;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileRenderer;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastRenderer;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanRenderer;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanSwordModel;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.FlameRingRenderer;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.PhantomBladeRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            EntityRegistry.LIZHI_YAN.get(),
            LizhiYanRenderer::new
        );

        // 飞剑冲击波渲染器
        event.registerEntityRenderer(
            EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
            LizhiYanBlastRenderer::new
        );

        // 破晦阵几何印记：收缩三角/八卦八边形
        event.registerEntityRenderer(
            EntityRegistry.GLOOMPURGE_MARK.get(),
            GloompurgeMarkRenderer::new
        );

        // 液氮炮弹：球体弹 + 冷雾拖尾 + 命中扩散环
        event.registerEntityRenderer(
            EntityRegistry.LNC_PROJECTILE.get(),
            LncProjectileRenderer::new
        );

        // 元素附着弧环：目标脚下四段弧（层数显示）
        event.registerEntityRenderer(
            EntityRegistry.ELEMENT_RING.get(),
            ElementRingRenderer::new
        );

        // 青霆剑：雷炁凝形插地能量剑
        event.registerEntityRenderer(
            EntityRegistry.SUNDERBLADE.get(),
            SunderbladeRenderer::new
        );

        // 目标标记环：惊霆诀引导水墨圆环
        event.registerEntityRenderer(
            EntityRegistry.TARGET_MARK.get(),
            TargetMarkRenderer::new
        );

        // 水墨雷瀑：惊霆诀首击/收尾瀑布雷柱
        event.registerEntityRenderer(
            EntityRegistry.INK_WATERFALL.get(),
            InkWaterfallRenderer::new
        );

        // 幻影魔剑：焚灭 × 主手武器协同投影（含挥砍弧线火焰刀光）
        event.registerEntityRenderer(
            EntityRegistry.PHANTOM_BLADE.get(),
            PhantomBladeRenderer::new
        );

        // 焚灭火环：斩击触发的贴地燃烧火环（16 帧序列贴图）
        event.registerEntityRenderer(
            EntityRegistry.FLAME_RING.get(),
            FlameRingRenderer::new
        );

        // 衔火血翼：V1 隐形载体（血萤云粒子表现）；V2 换 MC 蝙蝠换色渲染器
        event.registerEntityRenderer(
            EntityRegistry.BLOODWING.get(),
            NoopRenderer::new
        );
    }

    /**
     * 注册模型层定义（LayerDefinition）
     */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
            LizhiYanBlastRenderer.MODEL_LAYER_LOCATION,
            LizhiYanBlastRenderer::createBodyLayer
        );

        // 飞剑实体模型图层
        event.registerLayerDefinition(
            LizhiYanSwordModel.LAYER_LOCATION,
            LizhiYanSwordModel::createBodyLayer
        );

        // 青霆剑模型图层
        event.registerLayerDefinition(
            SunderbladeRenderer.LAYER_LOCATION,
            SunderbladeRenderer::createBodyLayer
        );
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        var particleType = ParticleRegistry.INK_ZAP_PARTICLE.get();
        event.registerSpriteSet(
            particleType,
            InkZapParticle.Provider::new
        );

        event.registerSpriteSet(
            ParticleRegistry.BLOOD_FIREFLY.get(),
            BloodFireflyParticle.Provider::new
        );
    }
}

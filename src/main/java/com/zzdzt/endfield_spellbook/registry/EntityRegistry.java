package com.zzdzt.endfield_spellbook.registry;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.ElementRingEntity;
import com.zzdzt.endfield_spellbook.element.InkWaterfallEntity;
import com.zzdzt.endfield_spellbook.element.SunderbladeEntity;
import com.zzdzt.endfield_spellbook.element.TargetMarkEntity;
import com.zzdzt.endfield_spellbook.spell.bloodwing.BloodwingEntity;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkEntity;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileEntity;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanEntity;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.FlameRingEntity;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.PhantomBladeEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EndfieldSpellbook.MOD_ID);

    // ========== 飞剑实体 ==========
    public static final RegistryObject<EntityType<LizhiYanEntity>> LIZHI_YAN =
        ENTITY_TYPES.register("lizhi_yan", () ->
            EntityType.Builder.<LizhiYanEntity>of(LizhiYanEntity::new, MobCategory.MISC)
                .sized(0.4f, 0.4f)
                .clientTrackingRange(64)
                .updateInterval(2)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "lizhi_yan").toString())
        );
    public static final RegistryObject<EntityType<LizhiYanBlastVisualEntity>> LIZHI_YAN_BLAST_VISUAL =
        ENTITY_TYPES.register("lizhi_yan_blast_visual", () ->
            EntityType.Builder.<LizhiYanBlastVisualEntity>of(LizhiYanBlastVisualEntity::new, MobCategory.MISC)
                .sized(0.1f, 0.1f)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "lizhi_yan_blast_visual").toString())
        );

    // 破晦阵几何印记实体（三角蓄力/八卦八边形，纯视觉）
    public static final RegistryObject<EntityType<GloompurgeMarkEntity>> GLOOMPURGE_MARK =
        ENTITY_TYPES.register("gloompurge_mark", () ->
            EntityType.Builder.<GloompurgeMarkEntity>of(GloompurgeMarkEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "gloompurge_mark").toString())
        );

    // 液氮炮弹（温蒂 S3：弹道弹体，命中后圆形范围结算）
    public static final RegistryObject<EntityType<LncProjectileEntity>> LNC_PROJECTILE =
        ENTITY_TYPES.register("lnc_projectile", () ->
            EntityType.Builder.<LncProjectileEntity>of(LncProjectileEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "lnc_projectile").toString())
        );

    // 元素附着弧环（附着层数视觉，跟随目标）
    // ⚠️ updateInterval 必须 = 1：弧环有自主滑行轨迹，低频同步会呈现"滑3停1"的锯齿感
    // 元素附着弧环（附着层数视觉，跟随目标）
    public static final RegistryObject<EntityType<ElementRingEntity>> ELEMENT_RING =
        ENTITY_TYPES.register("element_ring", () ->
            EntityType.Builder.<ElementRingEntity>of(ElementRingEntity::new, MobCategory.MISC)
                .sized(0.2f, 0.2f)
                .clientTrackingRange(64)
                .updateInterval(4)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "element_ring").toString())
        );

    // 青霆剑（雷炁凝形、插地固定，惊霆诀场资源）
    public static final RegistryObject<EntityType<SunderbladeEntity>> SUNDERBLADE =
        ENTITY_TYPES.register("sunderblade", () ->
            EntityType.Builder.<SunderbladeEntity>of(SunderbladeEntity::new, MobCategory.MISC)
                .sized(0.4f, 1.8f)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "sunderblade").toString())
        );

    // 目标标记环（惊霆诀引导：目标脚下水墨色圆环，固定不跟随）
    public static final RegistryObject<EntityType<TargetMarkEntity>> TARGET_MARK =
        ENTITY_TYPES.register("target_mark", () ->
            EntityType.Builder.<TargetMarkEntity>of(TargetMarkEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "target_mark").toString())
        );

    // 水墨雷瀑（惊霆诀首击/收尾的瀑布雷柱，纯视觉）
    public static final RegistryObject<EntityType<InkWaterfallEntity>> INK_WATERFALL =
        ENTITY_TYPES.register("ink_waterfall", () ->
            EntityType.Builder.<InkWaterfallEntity>of(InkWaterfallEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "ink_waterfall").toString())
        );

    // 幻影魔剑（焚灭 × 主手武器协同，纯视觉投影横扫）
    public static final RegistryObject<EntityType<PhantomBladeEntity>> PHANTOM_BLADE =
        ENTITY_TYPES.register("phantom_blade", () ->
            EntityType.Builder.<PhantomBladeEntity>of(PhantomBladeEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "phantom_blade").toString())
        );

    // 焚灭火环（魔剑协同·斩击贴地燃烧火环，纯视觉序列帧）
    public static final RegistryObject<EntityType<FlameRingEntity>> FLAME_RING =
        ENTITY_TYPES.register("flame_ring", () ->
            EntityType.Builder.<FlameRingEntity>of(FlameRingEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "flame_ring").toString())
        );

    // 衔火血翼（驱火焚影：俯冲/盘桓/转移/爆裂载体，V1 隐形 + 血萤云表现）
    public static final RegistryObject<EntityType<BloodwingEntity>> BLOODWING =
        ENTITY_TYPES.register("bloodwing", () ->
            EntityType.Builder.<BloodwingEntity>of(BloodwingEntity::new, MobCategory.MISC)
                .sized(0.4f, 0.4f)
                .clientTrackingRange(64)
                .updateInterval(1)   // 轨道自主运动，逐 tick 同步保平滑
                .build(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "bloodwing").toString())
        );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}

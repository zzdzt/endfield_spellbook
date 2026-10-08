package com.zzdzt.endfield_spellbook.registry;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 模组实体标签。
 */
public final class EntityTags {

    /** 时滞领域免疫（直接减少实体 Tick 属于极强控制，Boss 免疫）。 */
    public static final TagKey<EntityType<?>> TIME_IMMUNE =
        TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "time_immune"));

    /** 元素异常免疫（燃烧/导电/腐蚀/冻结的强控效果，Boss 免疫；伤害部分照常生效）。 */
    public static final TagKey<EntityType<?>> REACTION_IMMUNE =
        TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "reaction_immune"));

    private EntityTags() {
    }
}

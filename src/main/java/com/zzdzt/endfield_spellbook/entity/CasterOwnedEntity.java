package com.zzdzt.endfield_spellbook.entity;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 拥有直接所有者的模组实体（自定义弹体 / 召唤物等）。
 *
 * 标记直接所有者（发射者/召唤者），供伤害归属、友军豁免等逻辑使用：
 * 原方案只认 {@code Projectile#getOwner()} 与 {@code TamableAnimal}，
 * 覆盖不到本模组的自定义实体（液氮炮弹、飞剑、水墨雷击等）。
 */
public interface CasterOwnedEntity {

    /** 实体的直接所有者（发射者/召唤者），可为 null。 */
    @Nullable
    LivingEntity getDirectOwner();
}

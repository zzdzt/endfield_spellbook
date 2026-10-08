package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 元素附着的持久化存储（Forge 1.20.1 无 Data Attachment，走实体 persistentData ——
 * 与 {@code LiquidNitrogenMarkedEffect} 同款模式；persistentData 随实体保存/加载）。
 *
 * <p>单元素模型：同一敌人同时最多持有一组附着；异元素施加由 {@code EndfieldElements.apply}
 * 处理反应后清空。衰减为单时间戳刷新制：挂/叠层时刷新过期时间，到点全清。
 */
public final class ElementInflictionStore {

    private static final String NBT_ELEMENT = EndfieldSpellbook.MOD_ID + ":infliction_element";
    private static final String NBT_STACKS = EndfieldSpellbook.MOD_ID + ":infliction_stacks";
    private static final String NBT_EXPIRE = EndfieldSpellbook.MOD_ID + ":infliction_expire";

    private ElementInflictionStore() {
    }

    /** 当前附着是否有效（有元素且未过期）。 */
    public static boolean isValid(LivingEntity target, long gameTime) {
        CompoundTag data = target.getPersistentData();
        return data.contains(NBT_ELEMENT) && gameTime < data.getLong(NBT_EXPIRE);
    }

    /** 当前附着元素；无附着返回 null（不校验过期，调用方先 {@link #isValid}）。 */
    @Nullable
    public static EndfieldElement getElement(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (!data.contains(NBT_ELEMENT)) return null;
        EndfieldElement[] values = EndfieldElement.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, data.getInt(NBT_ELEMENT)));
        return values[ordinal];
    }

    public static int getStacks(LivingEntity target) {
        return target.getPersistentData().getInt(NBT_STACKS);
    }

    /** 写入附着（层数与过期时间由调用方算好）。 */
    public static void set(LivingEntity target, EndfieldElement element, int stacks, long expireAtGameTime) {
        CompoundTag data = target.getPersistentData();
        data.putInt(NBT_ELEMENT, element.ordinal());
        data.putInt(NBT_STACKS, stacks);
        data.putLong(NBT_EXPIRE, expireAtGameTime);
    }

    /** 清空附着。 */
    public static void clear(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(NBT_ELEMENT);
        data.remove(NBT_STACKS);
        data.remove(NBT_EXPIRE);
    }
}

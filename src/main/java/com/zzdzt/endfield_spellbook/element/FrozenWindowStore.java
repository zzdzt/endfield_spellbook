package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * 冻结异常易伤窗口的持久化存储（persistentData 模式）。
 *
 * <p>计数型资源：目标接下来 {@code hits} 次受到的伤害提高 {@code bonus}，
 * 每次命中消耗 1 次，用完窗口关闭；另设兜底时限防长期空挂。
 */
public final class FrozenWindowStore {

    private static final String NBT_HITS = EndfieldSpellbook.MOD_ID + ":frozen_hits";
    private static final String NBT_BONUS = EndfieldSpellbook.MOD_ID + ":frozen_bonus";
    private static final String NBT_EXPIRE = EndfieldSpellbook.MOD_ID + ":frozen_expire";

    private FrozenWindowStore() {
    }

    /** 窗口是否有效（剩余次数 > 0 且未过兜底时限）。 */
    public static boolean isValid(LivingEntity target, long gameTime) {
        CompoundTag data = target.getPersistentData();
        return data.getInt(NBT_HITS) > 0 && gameTime < data.getLong(NBT_EXPIRE);
    }

    public static float getBonusPct(LivingEntity target) {
        return target.getPersistentData().getFloat(NBT_BONUS);
    }

    public static int getHitsRemaining(LivingEntity target) {
        return target.getPersistentData().getInt(NBT_HITS);
    }

    /** 写入窗口（次数/增伤幅度/兜底时限）。 */
    public static void set(LivingEntity target, int hits, float bonusPct, long expireAtGameTime) {
        CompoundTag data = target.getPersistentData();
        data.putInt(NBT_HITS, hits);
        data.putFloat(NBT_BONUS, bonusPct);
        data.putLong(NBT_EXPIRE, expireAtGameTime);
    }

    /** 消耗 1 次次数；归零时关闭窗口。 */
    public static void decrementHits(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        int remaining = data.getInt(NBT_HITS) - 1;
        if (remaining <= 0) {
            clear(target);
        } else {
            data.putInt(NBT_HITS, remaining);
        }
    }

    public static void clear(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(NBT_HITS);
        data.remove(NBT_BONUS);
        data.remove(NBT_EXPIRE);
    }
}

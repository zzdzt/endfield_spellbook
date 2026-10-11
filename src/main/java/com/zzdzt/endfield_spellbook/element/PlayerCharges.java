package com.zzdzt.endfield_spellbook.element;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 玩家资源 API 与持久化存储。
 *
 * <p>资源通过显式 {@link #initialize} 按需启用。具有时间恢复策略的资源采用惰性结算：
 * 读取、修改、战斗事件和登出时才计算已满足条件的恢复层数，不再为每个资源做每 Tick 轮询。
 * 战斗时间戳由 {@link PlayerChargeCombatTracker} 统一维护；熔火反馈由
 * {@link MoltenFireFeedback} 管理。
 */
public final class PlayerCharges {
    /** 最后一次造成/承受伤害后，额外经过多少 tick 才算脱离战斗。 */
    public static final int COMBAT_TIMEOUT_TICKS = 100;

    private static final String KEY_PREFIX = "endfield_spellbook:charge_";

    private PlayerCharges() {
    }

    // ==================== API ====================

    /**
     * 显式初始化资源。已有资源不会被重置；猎矢由风矢穿林首次施放时初始化为 4 层。
     * peek 不会自动初始化，避免单纯查询意外授予资源。
     */
    public static void initialize(Entity player, PlayerChargeType type) {
        if (player.level().isClientSide) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        boolean wasInitialized = isInitialized(data, type);
        long now = PlayerChargeCombatTracker.gameTime(player);

        if (!wasInitialized) {
            data.putInt(stacksKey(type), type.getInitialStacks());
        }

        if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN
            && (!wasInitialized
                || !data.contains(lastRegenKey(type))
                || !data.getBoolean(clockSchemaKey(type)))) {
            // 兼容旧版本：旧时间戳使用了维度本地 gameTime；迁移时从现在开始计时。
            resetRegenClock(data, type, now);
        }

        if (type == PlayerChargeType.MOLTEN_FIRE) {
            MoltenFireFeedback.syncMark(player, getStacks(data, type));
        }
    }

    /** 资源是否曾被显式初始化或写入。层数为 0 仍然表示已初始化。 */
    public static boolean isInitialized(Entity player, PlayerChargeType type) {
        return isInitialized(player.getPersistentData(), type);
    }

    private static boolean isInitialized(CompoundTag data, PlayerChargeType type) {
        return data.contains(stacksKey(type));
    }

    /** 当前层数。未初始化时返回 0，不创建数据。时间恢复资源会先惰性结算。 */
    public static int peek(Entity player, PlayerChargeType type) {
        if (!isInitialized(player, type)) {
            return 0;
        }
        settleIfNeeded(player, type);
        return getStacks(player.getPersistentData(), type);
    }

    /** 是否达到上限。 */
    public static boolean isFull(Entity player, PlayerChargeType type) {
        return peek(player, type) >= type.getMaxStacks();
    }

    /** 增加层数（溢出截断到上限）。熔火满层反馈只在从未满层跨越到满层时触发。 */
    public static void gain(Entity player, PlayerChargeType type, int amount) {
        if (amount <= 0 || player.level().isClientSide) {
            return;
        }
        if (!isInitialized(player, type)) {
            initialize(player, type);
        }
        settleIfNeeded(player, type);

        CompoundTag data = player.getPersistentData();
        int before = getStacks(data, type);
        int after = (int) Math.min((long) type.getMaxStacks(), (long) before + amount);
        data.putInt(stacksKey(type), after);

        if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN
            && after >= type.getMaxStacks()) {
            // 满层时不累积隐藏的额外回充进度；消耗后从当前时刻重新计时。
            resetRegenClock(data, type, PlayerChargeCombatTracker.gameTime(player));
        }

        if (type == PlayerChargeType.MOLTEN_FIRE) {
            MoltenFireFeedback.syncMark(player, after);
            if (before < type.getMaxStacks() && after >= type.getMaxStacks()
                && player instanceof LivingEntity living) {
                MoltenFireFeedback.onBecameFull(living);
            }
        }
    }

    /** 消耗指定数量并返回实际消耗量；不足时只消耗现有层数。 */
    public static int consume(Entity player, PlayerChargeType type, int amount) {
        if (amount <= 0 || player.level().isClientSide || !isInitialized(player, type)) {
            return 0;
        }
        settleIfNeeded(player, type);

        CompoundTag data = player.getPersistentData();
        int current = getStacks(data, type);
        int consumed = Math.min(current, amount);
        if (consumed > 0) {
            int remaining = current - consumed;
            data.putInt(stacksKey(type), remaining);
            if (type == PlayerChargeType.MOLTEN_FIRE) {
                MoltenFireFeedback.syncMark(player, remaining);
            }
        }
        return consumed;
    }

    /** 消耗全部层数，返回消耗数量；未初始化资源不会被隐式创建。 */
    public static int consumeAll(Entity player, PlayerChargeType type) {
        if (player.level().isClientSide || !isInitialized(player, type)) {
            return 0;
        }
        settleIfNeeded(player, type);

        CompoundTag data = player.getPersistentData();
        int stacks = getStacks(data, type);
        data.putInt(stacksKey(type), 0);
        if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN) {
            resetRegenClock(data, type, PlayerChargeCombatTracker.gameTime(player));
        }
        if (type == PlayerChargeType.MOLTEN_FIRE) {
            MoltenFireFeedback.syncMark(player, 0);
        }
        return stacks;
    }

    /** 清空已初始化的资源，并从当前时刻重新开始其恢复计时。 */
    public static void clear(Entity player, PlayerChargeType type) {
        if (player.level().isClientSide || !isInitialized(player, type)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putInt(stacksKey(type), 0);
        if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN) {
            resetRegenClock(data, type, PlayerChargeCombatTracker.gameTime(player));
        }
        if (type == PlayerChargeType.MOLTEN_FIRE) {
            MoltenFireFeedback.syncMark(player, 0);
        }
    }

    // ==================== 惰性结算与生命周期 ====================

    private static void settleIfNeeded(Entity player, PlayerChargeType type) {
        if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN
            && isInitialized(player, type)) {
            settleRegen(player, type, PlayerChargeCombatTracker.gameTime(player));
        }
    }

    private static void settleRegen(Entity player, PlayerChargeType type, long now) {
        CompoundTag data = player.getPersistentData();
        String lastKey = lastRegenKey(type);
        int interval = type.getRegenIntervalTicks();
        if (interval <= 0) {
            return;
        }

        // 旧版本使用维度本地 gameTime；迁移到统一时钟时不追溯旧时间戳。
        if (!data.contains(lastKey) || !data.getBoolean(clockSchemaKey(type))) {
            resetRegenClock(data, type, now);
            return;
        }

        if (PlayerChargeCombatTracker.inCombat(player)) {
            // 战斗中彻底清除回充进度，和旧版在线 Tick 规则保持一致。
            resetRegenClock(data, type, now);
            return;
        }

        long last = data.getLong(lastKey);
        Long lastCombat = PlayerChargeCombatTracker.lastCombatTick(player);
        long eligibleFrom = last;
        if (lastCombat != null) {
            eligibleFrom = Math.max(eligibleFrom, lastCombat + COMBAT_TIMEOUT_TICKS);
        }

        // 战斗结束后的 100 tick 冷却不算回充时间。
        if (now < eligibleFrom) {
            data.putLong(lastKey, eligibleFrom);
            return;
        }

        int current = getStacks(data, type);
        if (current >= type.getMaxStacks()) {
            resetRegenClock(data, type, now);
            return;
        }

        long elapsed = now - eligibleFrom;
        long intervals = elapsed / interval;
        if (intervals <= 0) {
            if (eligibleFrom > last) {
                data.putLong(lastKey, eligibleFrom);
            }
            return;
        }

        int gained = (int) Math.min((long) type.getMaxStacks(), intervals);
        int after = Math.min(type.getMaxStacks(), current + gained);
        data.putInt(stacksKey(type), after);

        if (after >= type.getMaxStacks()) {
            // 达到上限后丢弃剩余进度；满层期间不能偷偷蓄积回充。
            resetRegenClock(data, type, now);
        } else {
            // 只推进完整回充周期，保留不足一个周期的在线进度。
            data.putLong(lastKey, eligibleFrom + intervals * interval);
        }
    }

    /**
     * 玩家造成/承受伤害时由 CombatTracker 调用。
     * 先结算战斗开始前的有效在线时间，再重置计时基准，并处理已初始化的战斗开始型资源。
     */
    static void onCombatActivity(ServerPlayer player, long now, boolean wasInCombat) {
        CompoundTag data = player.getPersistentData();
        for (PlayerChargeType type : PlayerChargeType.values()) {
            if (!isInitialized(data, type)) {
                continue;
            }

            if (type.getPolicy() == PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN) {
                settleRegen(player, type, now);
                resetRegenClock(data, type, now);
            } else if (!wasInCombat
                && type.getPolicy() == PlayerChargeType.ChargePolicy.BATTLE_START_SET) {
                data.putInt(stacksKey(type), type.getBattleStartValue());
            }
        }
    }

    /** 登出前先结算在线进度，再保存不足一个周期的余量并暂停时钟。 */
    static void pauseForLogout(ServerPlayer player) {
        long now = PlayerChargeCombatTracker.gameTime(player);
        CompoundTag data = player.getPersistentData();

        for (PlayerChargeType type : PlayerChargeType.values()) {
            if (!isInitialized(data, type)
                || type.getPolicy() != PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN) {
                continue;
            }

            settleRegen(player, type, now);
            int progress = 0;
            if (!PlayerChargeCombatTracker.inCombat(player)
                && getStacks(data, type) < type.getMaxStacks()) {
                long last = data.getLong(lastRegenKey(type));
                progress = (int) Math.max(0L, Math.min(
                    (long) type.getRegenIntervalTicks() - 1L, now - last));
            }
            data.putInt(regenProgressKey(type), progress);
            data.putLong(lastRegenKey(type), now);
        }
    }

    /** 清理旧版为每种资源重复写入的战斗状态键；新实现由 CombatTracker 单独维护。 */
    static void cleanupLegacyCombatState(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        for (PlayerChargeType type : PlayerChargeType.values()) {
            data.remove(KEY_PREFIX + type.name() + "_was_in_combat");
        }
    }

    /** 登录时恢复在线余量，但将离线时间排除在回充之外。 */
    static void resumeAfterLogin(ServerPlayer player) {
        long now = PlayerChargeCombatTracker.gameTime(player);
        CompoundTag data = player.getPersistentData();

        for (PlayerChargeType type : PlayerChargeType.values()) {
            if (!isInitialized(data, type)
                || type.getPolicy() != PlayerChargeType.ChargePolicy.OUT_OF_COMBAT_REGEN) {
                continue;
            }

            int progress = Math.max(0, Math.min(
                type.getRegenIntervalTicks() - 1, data.getInt(regenProgressKey(type))));
            if (getStacks(data, type) >= type.getMaxStacks()) {
                progress = 0;
            }
            resetRegenClock(data, type, now - progress);
        }
    }

    private static void resetRegenClock(CompoundTag data, PlayerChargeType type, long now) {
        data.putLong(lastRegenKey(type), now);
        data.putInt(regenProgressKey(type), 0);
        data.putBoolean(clockSchemaKey(type), true);
    }

    // ==================== NBT 键 ====================

    private static int getStacks(CompoundTag data, PlayerChargeType type) {
        return Math.max(0, Math.min(type.getMaxStacks(), data.getInt(stacksKey(type))));
    }

    private static String stacksKey(PlayerChargeType type) {
        return KEY_PREFIX + type.name() + "_stacks";
    }

    private static String lastRegenKey(PlayerChargeType type) {
        return KEY_PREFIX + type.name() + "_last_regen";
    }

    private static String regenProgressKey(PlayerChargeType type) {
        return KEY_PREFIX + type.name() + "_regen_progress";
    }

    private static String clockSchemaKey(PlayerChargeType type) {
        return KEY_PREFIX + type.name() + "_clock_v2";
    }
}

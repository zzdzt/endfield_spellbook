package com.zzdzt.endfield_spellbook.element;

/**
 * 终末地式玩家资源类型。
 *
 * <p>此枚举只声明资源规则；初始化、结算、战斗状态与视觉反馈由独立管理器负责。
 */
public enum PlayerChargeType {

    /**
     * 熔火（焚灭·莱万汀化）：吸收灼热附着转化，满 4 层触发强化焚灭。
     * 不自动恢复，死亡后随玩家实体状态重建。
     */
    MOLTEN_FIRE(ChargePolicy.NONE, 4, 0, 0, 0),

    // ==================== 预留资源（尚未接入玩法） ====================

    /** 启示：首次初始化为 0；进入战斗时重置为 4，上限 8。 */
    SIGNS(ChargePolicy.BATTLE_START_SET, 8, 0, 4, 0),

    /** 猎矢：首次使用风矢穿林时初始化为 4；在线脱战每 40 tick 恢复 1 层。 */
    HUNTING_ARROWS(ChargePolicy.OUT_OF_COMBAT_REGEN, 4, 4, 0, 40);

    /** 资源恢复策略。 */
    public enum ChargePolicy {
        /** 无自动恢复（熔火）。 */
        NONE,
        /** 在线脱战时按间隔逐层恢复。 */
        OUT_OF_COMBAT_REGEN,
        /** 从非战斗进入战斗时重置为指定值。 */
        BATTLE_START_SET
    }

    private final ChargePolicy policy;
    private final int maxStacks;
    private final int initialStacks;
    private final int battleStartValue;
    private final int regenIntervalTicks;

    PlayerChargeType(ChargePolicy policy, int maxStacks, int initialStacks,
                     int battleStartValue, int regenIntervalTicks) {
        this.policy = policy;
        this.maxStacks = maxStacks;
        this.initialStacks = initialStacks;
        this.battleStartValue = battleStartValue;
        this.regenIntervalTicks = regenIntervalTicks;
    }

    public ChargePolicy getPolicy() {
        return policy;
    }

    public int getMaxStacks() {
        return maxStacks;
    }

    /** 首次显式初始化时给予的层数。 */
    public int getInitialStacks() {
        return initialStacks;
    }

    public int getBattleStartValue() {
        return battleStartValue;
    }

    /** 脱战回充间隔（tick/层）；0 表示无回充。 */
    public int getRegenIntervalTicks() {
        return regenIntervalTicks;
    }
}

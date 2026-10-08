package com.zzdzt.endfield_spellbook.element;

/**
 * 终末地式玩家资源类型（通用容器：熔火 = 首实例）。
 *
 * <p>每个资源类型自带：恢复策略、层数上限、战斗开始初始值、脱战回充间隔——
 * 未来同类机制（如提弗洛斯：启示上限 8、战斗开始重置为 4；猎矢上限 4、脱战逐渐回满）
 * 都只是新枚举条目的参数差异，结构零改动。
 */
public enum PlayerChargeType {

    /**
     * 熔火（焚灭·莱万汀化）：吸收灼热附着转化，满 4 层触发强化焚灭。
     * 不衰减不回充，死亡随实体重建自动清空。
     */
    MOLTEN_FIRE(ChargePolicy.NONE, 4, 0, 0),

    // ==================== 预留（提弗洛斯式，未来接线时启用） ====================

    /** 启示（预留）：战斗开始时重置为 4，上限 8。 */
    SIGNS(ChargePolicy.BATTLE_START_SET, 8, 4, 0),
    /** 猎矢（预留）：脱离战斗后每 2 秒回充 1 层至满，上限 4。 */
    HUNTING_ARROWS(ChargePolicy.OUT_OF_COMBAT_REGEN, 4, 0, 40);

    /** 资源恢复策略。 */
    public enum ChargePolicy {
        /** 无自动恢复（熔火）。 */
        NONE,
        /** 脱离战斗后按间隔逐层回充至满（猎矢）。 */
        OUT_OF_COMBAT_REGEN,
        /** 战斗开始（非战斗 → 战斗的转换沿）时重置为初始值（启示）。 */
        BATTLE_START_SET
        // 预留：PER_STACK_FADE（逐层消退）
    }

    private final ChargePolicy policy;
    private final int maxStacks;
    private final int battleStartValue;
    private final int regenIntervalTicks;

    PlayerChargeType(ChargePolicy policy, int maxStacks, int battleStartValue, int regenIntervalTicks) {
        this.policy = policy;
        this.maxStacks = maxStacks;
        this.battleStartValue = battleStartValue;
        this.regenIntervalTicks = regenIntervalTicks;
    }

    public ChargePolicy getPolicy() {
        return policy;
    }

    public int getMaxStacks() {
        return maxStacks;
    }

    public int getBattleStartValue() {
        return battleStartValue;
    }

    /** 脱战回充间隔（tick/层）；0 表示无回充。 */
    public int getRegenIntervalTicks() {
        return regenIntervalTicks;
    }
}

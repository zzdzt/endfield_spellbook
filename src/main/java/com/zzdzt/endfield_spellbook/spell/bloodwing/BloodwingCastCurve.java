package com.zzdzt.endfield_spellbook.spell.bloodwing;

/**
 * 驱火焚影时间轴常量与纯函数真源（参照 FlameRingCastCurve / ThunderCastCurve 模式）。
 * 血翼实体、法术类、（V2）渲染器三方引用；调节奏只改这里。
 */
public final class BloodwingCastCurve {
    private BloodwingCastCurve() {
    }

    // ==================== 生命周期 ====================

    /** 血翼总寿命（tick）：盘桓 10 秒，含俯冲与爆裂蓄势。 */
    public static final int LIFETIME_TICKS = 200;
    /** 出生上浮时长（tick）。 */
    public static final int ASCEND_TICKS = 5;
    /** 俯冲时长（tick）。 */
    public static final int DIVE_TICKS = 12;
    /** 俯冲缓入幂次（>1 = 起步慢末段快，速度即打击感）。 */
    public static final float DIVE_EASE_POWER = 1.6f;

    // ==================== 盘桓轨道 ====================

    /** 轨道半径（格）。 */
    public static final float ORBIT_RADIUS = 1.6f;
    /** 角速度（rad/tick）：0.15 → 约 2.1s 一圈。 */
    public static final float ORBIT_ANGULAR = 0.15f;
    /** 上下浮动幅度（格）。 */
    public static final float ORBIT_BOB_AMP = 0.4f;
    /** 上下浮动频率（rad/tick）。 */
    public static final float ORBIT_BOB_FREQ = 0.12f;

    // ==================== Debuff 续杯 / 盘桓灼烧 ====================

    /** 续杯间隔（tick）。 */
    public static final int DEBUFF_REFRESH_INTERVAL = 20;
    /** 单次续杯时长（tick）：虚弱 / 灼热脆弱共用。 */
    public static final int DEBUFF_DURATION = 60;
    /** 盘桓灼烧间隔（tick）：每秒一次 AoE 伤害。 */
    public static final int ORBIT_DAMAGE_INTERVAL = 20;
    /** 盘桓灼烧单次伤害倍率（× 法术强度，主目标为中心 IMPACT_RADIUS 范围）。 */
    public static final float ORBIT_DAMAGE_FRACTION = 0.3f;

    // ==================== 转移 ====================

    /** 目标死亡后的索敌半径（格）。 */
    public static final double RETARGET_RANGE = 8.0;
    /** 最大转移次数。 */
    public static final int MAX_TRANSFERS = 3;

    // ==================== 爆裂（Recast 二段收尾） ====================

    /** 蓄势收束时长（tick）。 */
    public static final int BURST_WINDUP_TICKS = 4;
    /** 首击 AoE 半径（格）：仅伤害，附着与 debuff 仅主目标。 */
    public static final float IMPACT_RADIUS = 2.0f;

    // ==================== 伤害倍率（× 法术强度） ====================

    /** 首击 AoE 伤害。 */
    public static final float IMPACT_DAMAGE_FRACTION = 0.8f;
    /** 爆裂额外伤害（单体）。 */
    public static final float BURST_DAMAGE_FRACTION = 0.6f;

    /** 俯冲进度 → 缓入插值（纯函数，实体运动与 V2 渲染共用真源）。 */
    public static float diveProgress(int tick) {
        float p = Math.min(1f, tick / (float) DIVE_TICKS);
        return (float) Math.pow(p, DIVE_EASE_POWER);
    }
}

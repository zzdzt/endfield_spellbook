package com.zzdzt.endfield_spellbook.spell.tidalwave;

/**
 * 踏潮卷浪时序/数值常量表（参照 FlameRingCastCurve/ThunderCastCurve 惯例）。
 * 调技能节奏只改这里。
 */
public final class TidalWaveCastCurve {

    /** 冲刺窗口（tick）：MobEffect 持续时长，撞墙/贴脸可提前触发冲击。 */
    public static final int DASH_WINDOW_TICKS = 8;

    /** 潮汐冲击半径（格）。 */
    public static final float IMPACT_RADIUS = 3.5f;

    /** 涡流存场时长（tick）：30 秒。 */
    public static final int WHIRLPOOL_LIFETIME_TICKS = 20 * 30;

    /** 场上涡流上限。 */
    public static final int WHIRLPOOL_MAX = 2;

    /** 施法时消耗涡流的检测半径（格，占位待调）。 */
    public static final double WHIRLPOOL_CONSUME_RANGE = 24.0;

    /** 涡流所有者钳制扫描半径（格）：生成新涡流前按所有者全场计数。 */
    public static final double WHIRLPOOL_OWNED_SCAN_RANGE = 64.0;

    /** 水龙卷持续时间（tick）：6 秒。 */
    public static final int TORNADO_LIFETIME_TICKS = 120;

    /** 水龙卷范围（格）。 */
    public static final float TORNADO_RADIUS = 3.5f;

    /** 水龙卷伤害结算间隔（tick）。 */
    public static final int TORNADO_TICK_INTERVAL = 10;

    /** 冲刺窗数据新鲜度上限（tick）：超出视为登出/死亡残留，不再生成水龙卷。 */
    public static final long PENDING_STALE_TICKS = 20;

    private TidalWaveCastCurve() {
    }
}

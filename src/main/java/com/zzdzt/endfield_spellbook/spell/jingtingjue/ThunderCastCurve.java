package com.zzdzt.endfield_spellbook.spell.jingtingjue;

/**
 * 青霆剑诀雷击时间轴真源。
 *
 * <p>"伤害解绑雷击帧"的时间轴：首击落雷 → 每柄剑错峰引导雷 → 收尾最后一击（×6），
 * 伤害与特效全部排到各自雷击帧结算（演出帧 = 命中帧）。
 * 施法侧（{@code SunderbladeStrikeSpell} 排队）只认这里的常量——调打击感节奏只改本文件。
 *
 * <p>纯函数 + 常量的 final 工具类，不建接口体系。
 */
public final class ThunderCastCurve {

    // ---------- 一次施放的雷击帧序列（SunderbladeStrikeSpell 排队，延迟为服务端游戏时间偏移） ----------

    /** 首击落雷延迟（tick）：瀑布雷瀑 + 落雷伤害。 */
    public static final long FIRST_STRIKE_DELAY = 4;
    /** 引导阶段：第一道剑雷的延迟基数（tick，实际除以 {@link #GUIDANCE_SPEED}）。 */
    public static final long GUIDANCE_BASE_DELAY = 14;
    /** 引导阶段：相邻两道剑雷的错峰间隔（tick，实际除以 {@link #GUIDANCE_SPEED}）。 */
    public static final long GUIDANCE_STEP = 5;
    /** 收尾雷：末道剑雷之后的间隔（tick，实际除以 {@link #GUIDANCE_SPEED}）。 */
    public static final long FINALE_GAP = 12;
    /** 引导阶段整体速度倍率（>1 加速）：作用于剑雷错峰与收尾时点。 */
    public static final double GUIDANCE_SPEED = 1.4;

    private ThunderCastCurve() {
    }
}

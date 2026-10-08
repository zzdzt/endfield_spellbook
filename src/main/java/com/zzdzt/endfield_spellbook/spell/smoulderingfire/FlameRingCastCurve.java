package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import net.minecraft.util.Mth;

/**
 * 焚灭大回环斩时间轴真源（魔剑 × 火环共用）。
 *
 * <p>节奏：{@link #SLASH_TICKS} 剑砍（剑尖=环头）→ {@link #PROPAGATE_TICKS} 火焰自传播
 * → {@link #BURN_TICKS} 满环燃烧 → {@link #FADE_TICKS} 溃散切向火星。
 * 服务端（粒子编排节奏）与客户端（Ribbon 渲染器）只认这里的常量与函数，
 * 调节奏只改本文件——魔剑与火环自动同步。
 *
 * <p>纯函数 + 常量的 final 工具类，不建接口体系（当前仅焚灭一咒消费）。
 */
public final class FlameRingCastCurve {

    /** 剑砍段（tick）：幻影魔剑扫掠窗，环的前段严格跟随剑（剑尖即环头）。 */
    public static final int SLASH_TICKS = 5;
    /** 火焰自传播时长（tick）：剑砍完自身弧段后，火顺着剑势跑完剩余弧。 */
    public static final int PROPAGATE_TICKS = 6;
    /** 满环燃烧时长（tick）。 */
    public static final int BURN_TICKS = 4;
    /** 溃散时长（tick）：切向火星爆发收尾。 */
    public static final int FADE_TICKS = 5;
    /** 播放总时长（tick）= 四段之和。 */
    public static final int LIFETIME = SLASH_TICKS + PROPAGATE_TICKS + BURN_TICKS + FADE_TICKS;

    /** 魔剑前置衔接段占比：从背后浮位加速飞向劈砍起点（环的剑砍段与魔剑扫掠窗对齐）。 */
    public static final double TRANSITION_FRACTION = 0.2;

    private FlameRingCastCurve() {
    }

    /**
     * t 时刻已揭示的回环角度（度）——"剑砍出环"的核心曲线：
     * 前段严格跟随幻影魔剑扫掠（剑尖即环头），剑落后火焰按 easeOut 自传播跑满剩余弧。
     *
     * @param t         实体存活 tick（可含 partialTicks）
     * @param bladeSpan 魔剑扫掠弧段（度，取绝对值）
     */
    public static float revealedDegrees(float t, float bladeSpan) {
        float tf = (float) TRANSITION_FRACTION;
        // 剑砍段：与剑的扫掠进度完全同步（衔接段不画环）
        float bladeP = Mth.clamp((t / SLASH_TICKS - tf) / (1f - tf), 0f, 1f);
        float deg = bladeP * bladeSpan;
        if (t > SLASH_TICKS) {
            // 自传播段：火焰顺剑势加速跑完剩余弧
            float propP = Mth.clamp((t - SLASH_TICKS) / (float) PROPAGATE_TICKS, 0f, 1f);
            deg = bladeSpan + (360f - bladeSpan) * (1f - (1f - propP) * (1f - propP));
        }
        return deg;
    }
}

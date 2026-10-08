package com.zzdzt.endfield_spellbook.client.fx;

import com.zzdzt.endfield_spellbook.config.ClientConfig;
import com.zzdzt.endfield_spellbook.config.VfxQuality;

/**
 * 法术特效 LOD（距离 × 质量档位）。client-only。
 *
 * 设计原则：**HIGH 档 + 任何距离 = 全量**，与引入 LOD 之前逐像素一致；
 * 降级只发生在 MEDIUM/LOW 档或远距离，且只砍"非结构性"的层
 * （辉光叠加、细分密度、柱子数量），不砍主结构（八卦爻线、接地环、地面波纹）。
 *
 * 用法：renderer 每帧取一次相机距离，调用下列方法决定画什么、画多密。
 */
public final class FxLod {

    private FxLod() {
    }

    /** 距离阈值（格） */
    private static final double NEAR = 24.0;
    private static final double MID = 32.0;
    private static final double FAR = 56.0;

    public static VfxQuality quality() {
        return ClientConfig.VFX_QUALITY.get();
    }

    /**
     * 辉光层（宽而淡的叠加层）是否绘制。
     * HIGH 恒 true；MEDIUM 超过 32 格砍掉；LOW 全砍。
     */
    public static boolean glowLayer(double dist) {
        return switch (quality()) {
            case HIGH -> true;
            case MEDIUM -> dist <= MID;
            case LOW -> false;
        };
    }

    /**
     * 能量膜是否用半段网格（24 段而非 48 段）。
     * ⚠️ 不是"每两段跳一段"——那样圆柱面会破洞；而是切换到另一套预构建的 24 段网格。
     */
    public static boolean membraneHalf(double dist) {
        return switch (quality()) {
            case HIGH -> false;
            case MEDIUM -> dist > FAR;
            case LOW -> dist > NEAR;
        };
    }

    /**
     * 球面/曲面细分档位（rings 与 segments 共用）。
     * HIGH 恒全量；MEDIUM 超过 32 格减半；LOW 超过 24 格取三分之一。
     * 下限保护 3 —— FxGeometry.sphere 要求 rings ≥1、segments ≥3。
     */
    public static int sphereDetail(double dist, int full) {
        return switch (quality()) {
            case HIGH -> full;
            case MEDIUM -> dist > MID ? Math.max(3, full / 2) : full;
            case LOW -> dist > NEAR ? Math.max(3, full / 3) : full;
        };
    }

    /**
     * 放射触须数量档位。Fibonacci 球面分布对任意 N 都保持均匀，
     * 减量只会让触须变稀疏、不会破坏分布 —— 与"跳根会破洞"的连续膜面不同，可直接除。
     */
    public static int rayCount(double dist, int full) {
        return switch (quality()) {
            case HIGH -> full;
            case MEDIUM -> dist > MID ? Math.max(6, full / 2) : full;
            case LOW -> dist > NEAR ? Math.max(4, full / 3) : full;
        };
    }

    /**
     * 能量柱步进（1 = 全画，2 = 隔一根，3 = 三取一）。
     * 柱子是分立元素，跳根不会破洞（不似连续膜面）。破晦阵能量柱在用。
     */
    public static int pillarStep(double dist) {
        return switch (quality()) {
            case HIGH -> 1;
            case MEDIUM -> dist > FAR ? 2 : 1;
            case LOW -> dist > NEAR ? 3 : 1;
        };
    }
}

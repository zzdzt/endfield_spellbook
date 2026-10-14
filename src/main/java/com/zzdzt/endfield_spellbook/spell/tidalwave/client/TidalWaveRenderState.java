package com.zzdzt.endfield_spellbook.spell.tidalwave.client;

import com.zzdzt.endfield_spellbook.spell.tidalwave.TidalWaveCastCurve;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WaterTornadoEntity;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WhirlpoolEntity;
import net.minecraft.util.Mth;

/**
 * 踏潮卷浪渲染状态（≈WindArrowRenderState，规范 §7）：
 * 全部随时间变化的视觉参数（sin/cos/clamp(age) 曲线）的唯一来源。
 * Renderer 只按本状态生成顶点，不做时间曲线计算。
 */
public final class TidalWaveRenderState {

    /** 插值年龄（tick）。 */
    public final float age;

    // ===== 涡流曲线 =====
    /** 外环旋转角（度）。 */
    public final float whirlpoolSpin;
    /** 淡入淡出系数 0~1（头尾各 10t）。 */
    public final float whirlpoolFade;

    // ===== 水龙卷曲线 =====
    /** 升起进度 0~1（前 10t）。 */
    public final float riseProgress;
    /** 消散透明度 0~1（尾 10t 淡出）。 */
    public final float tornadoFade;
    /** 外层螺旋角（度）。 */
    public final float twistOuter;
    /** 内层反向螺旋角（度）。 */
    public final float twistInner;

    private TidalWaveRenderState(float age, float whirlpoolSpin, float whirlpoolFade,
                                 float riseProgress, float tornadoFade,
                                 float twistOuter, float twistInner) {
        this.age = age;
        this.whirlpoolSpin = whirlpoolSpin;
        this.whirlpoolFade = whirlpoolFade;
        this.riseProgress = riseProgress;
        this.tornadoFade = tornadoFade;
        this.twistOuter = twistOuter;
        this.twistInner = twistInner;
    }

    public static TidalWaveRenderState createForWhirlpool(WhirlpoolEntity entity, float partialTicks) {
        float age = entity.tickCount + partialTicks;
        float remaining = entity.getLifetime() - entity.tickCount;
        float fade = Mth.clamp(Math.min(age, remaining) / 10f, 0f, 1f);
        return new TidalWaveRenderState(age, age * 4f, fade, 1f, 1f, 0f, 0f);
    }

    public static TidalWaveRenderState createForTornado(WaterTornadoEntity entity, float partialTicks) {
        float age = entity.tickCount + partialTicks;
        float rise = Mth.clamp(age / 10f, 0f, 1f);
        float fade = Mth.clamp((TidalWaveCastCurve.TORNADO_LIFETIME_TICKS - age) / 10f, 0f, 1f);
        return new TidalWaveRenderState(age, 0f, 1f, rise, fade, age * 9f, -age * 13f);
    }

    public boolean visible() {
        return whirlpoolFade > 0.01f && tornadoFade > 0.01f;
    }
}

package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 风矢视觉的单帧渲染状态。
 *
 * <p>集中保存渲染编排所需的时序、进度、alpha 曲线与空间量，由 {@link WindArrowVfxRenderer}
 * 每帧创建一次，供轨迹/前锋/命中三个渲染通道共享；只做数值推导，不持有可变状态。
 * 仅依赖 age 的共享动画计算（前锋脉冲、星链余痕 alpha、命中年龄）在此收口，
 * 渲染器内只保留依赖顶点序的几何计算。
 */
public final class WindArrowRenderState {

    /** 命中爆闪起始年龄：箭矢前锋抵达弹道终点的时刻。 */
    private static final float IMPACT_START_AGE = 3.0f;

    public final float age;
    public final float flightProgress;
    public final float visibleLength;
    public final float activeTrailLength;
    public final float beamAlpha;
    public final float arcAlpha;
    public final float trailAlpha;
    /** 星链余痕 alpha：trailAlpha × 显现残量曲线，仅依赖 age。 */
    public final float afterglowAlpha;
    /** 前锋脉冲：几何楔形呼吸系数，仅依赖 age。 */
    public final float headPulse;
    /** 前锋脉冲：贴图前锋呼吸系数（与几何楔形系数不同），仅依赖 age。 */
    public final float headTexturePulse;
    /** 命中爆闪年龄（age − 3.0，负值表示前锋尚未抵达）。 */
    public final float impactAge;
    /** 命中爆闪进度与衰减：普通命中（3s 曲线）与终结放射（5.5s 曲线）各一组，仅依赖 age。 */
    public final float impactProgressNormal;
    public final float impactFadeNormal;
    public final float impactProgressFinal;
    public final float impactFadeFinal;
    /** 贴图前锋三面慢旋基准角（age × 2.5）。 */
    public final float headTextureSpin;
    /** 尖端星芒旋转角（age × 21）。 */
    public final float tipGlintSpin;
    public final Vec3 originWorld;
    public final Vec3 direction;
    public final Vec3 cameraWorld;
    /** 实体当前视觉样式（STYLE_WIND_ARROW_*），命中通道据此选择爆闪贴图。 */
    public final int visualStyle;
    /** 弹道局部坐标系的抬升量与朝向：由实体包围盒半高与 yaw/pitch 构成。 */
    public final float bodyHalfHeight;
    public final float yawDegrees;
    public final float pitchDegrees;

    private WindArrowRenderState(float age, float flightProgress, float visibleLength,
                                 float activeTrailLength, float beamAlpha, float arcAlpha,
                                 float trailAlpha, float afterglowAlpha, float headPulse,
                                 float headTexturePulse, float impactAge,
                                 float impactProgressNormal, float impactFadeNormal,
                                 float impactProgressFinal, float impactFadeFinal,
                                 float headTextureSpin, float tipGlintSpin,
                                 Vec3 originWorld, Vec3 direction, Vec3 cameraWorld,
                                 int visualStyle, float bodyHalfHeight,
                                 float yawDegrees, float pitchDegrees) {
        this.age = age;
        this.flightProgress = flightProgress;
        this.visibleLength = visibleLength;
        this.activeTrailLength = activeTrailLength;
        this.beamAlpha = beamAlpha;
        this.arcAlpha = arcAlpha;
        this.trailAlpha = trailAlpha;
        this.afterglowAlpha = afterglowAlpha;
        this.headPulse = headPulse;
        this.headTexturePulse = headTexturePulse;
        this.impactAge = impactAge;
        this.impactProgressNormal = impactProgressNormal;
        this.impactFadeNormal = impactFadeNormal;
        this.impactProgressFinal = impactProgressFinal;
        this.impactFadeFinal = impactFadeFinal;
        this.headTextureSpin = headTextureSpin;
        this.tipGlintSpin = tipGlintSpin;
        this.originWorld = originWorld;
        this.direction = direction;
        this.cameraWorld = cameraWorld;
        this.visualStyle = visualStyle;
        this.bodyHalfHeight = bodyHalfHeight;
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = pitchDegrees;
    }

    public static WindArrowRenderState create(LizhiYanBlastVisualEntity entity, float partialTicks) {
        float age = entity.tickCount + partialTicks;
        float flightProgress = Mth.clamp((age + 0.35f) / 3.35f, 0.0f, 1.0f);
        float visibleLength = Math.min(entity.distance,
            Math.max(0.05f, entity.distance * flightProgress));
        // 拖尾要在常见战斗距离下形成完整层次；固定 6~10 格容易显得像短光点。
        float activeTrailLength = Math.min(visibleLength,
            Mth.clamp(entity.distance * 0.58f, 8.0f, 16.0f));

        float beamAlpha = 1.0f - Mth.clamp((age - 3.45f) / 1.50f, 0.0f, 1.0f);
        float arcAlpha = 1.0f - Mth.clamp((age - 3.65f) / 2.65f, 0.0f, 1.0f);
        float trailAlpha = 1.0f - Mth.clamp((age - 3.0f) / 7.0f, 0.0f, 1.0f);
        float revealResidual = Mth.clamp((age - 1.25f) / 1.55f, 0.0f, 1.0f);
        float afterglowAlpha = trailAlpha * (0.12f + 0.48f * revealResidual);
        float headPulse = 0.92f + 0.08f * Mth.sin(age * 1.7f);
        float headTexturePulse = 0.94f + 0.06f * Mth.sin(age * 1.7f);
        float impactAge = age - IMPACT_START_AGE;
        float impactProgressNormal = Mth.clamp(impactAge / 3.0f, 0.0f, 1.0f);
        float impactFadeNormal = 1.0f - impactProgressNormal;
        float impactProgressFinal = Mth.clamp(impactAge / 5.5f, 0.0f, 1.0f);
        float impactFadeFinal = 1.0f - impactProgressFinal;
        float headTextureSpin = age * 2.5f;
        float tipGlintSpin = age * 21.0f;

        Vec3 originWorld = new Vec3(
            Mth.lerp(partialTicks, entity.xo, entity.getX()),
            Mth.lerp(partialTicks, entity.yo, entity.getY()),
            Mth.lerp(partialTicks, entity.zo, entity.getZ())
        );
        Vec3 direction = entity.getBlastDirection();
        if (direction.lengthSqr() < 1.0e-6) {
            direction = new Vec3(0.0, 0.0, 1.0);
        } else {
            direction = direction.normalize();
        }
        Vec3 cameraWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        return new WindArrowRenderState(age, flightProgress, visibleLength, activeTrailLength,
            beamAlpha, arcAlpha, trailAlpha, afterglowAlpha, headPulse, headTexturePulse,
            impactAge, impactProgressNormal, impactFadeNormal, impactProgressFinal,
            impactFadeFinal, headTextureSpin, tipGlintSpin, originWorld, direction, cameraWorld,
            entity.getVisualStyle(),
            (float) entity.getBoundingBox().getYsize() * 0.5f,
            entity.getYRot(), entity.getXRot());
    }

    /** 主束与拖尾完全淡出后整帧可跳过。 */
    public boolean isExpired() {
        return trailAlpha <= 0.001f;
    }

    /** 弹道短到不可见时整帧可跳过。 */
    public boolean visible() {
        return visibleLength > 0.01f;
    }
}

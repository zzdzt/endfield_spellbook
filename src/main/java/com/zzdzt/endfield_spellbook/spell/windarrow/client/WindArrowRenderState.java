package com.zzdzt.endfield_spellbook.spell.windarrow.client;

import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 风矢视觉的单帧渲染状态。
 *
 * <p>集中保存渲染编排所需的时序、进度与空间量，由 {@link WindArrowVfxRenderer}
 * 每帧创建一次，供轨迹/前锋/命中三个渲染通道共享；只做数值推导，不持有可变状态。
 */
public final class WindArrowRenderState {
    public final float age;
    public final float flightProgress;
    public final float visibleLength;
    public final float activeTrailLength;
    public final float beamAlpha;
    public final float arcAlpha;
    public final float trailAlpha;
    public final Vec3 originWorld;
    public final Vec3 direction;
    public final Vec3 cameraWorld;

    private WindArrowRenderState(float age, float flightProgress, float visibleLength,
                                 float activeTrailLength, float beamAlpha, float arcAlpha,
                                 float trailAlpha, Vec3 originWorld, Vec3 direction,
                                 Vec3 cameraWorld) {
        this.age = age;
        this.flightProgress = flightProgress;
        this.visibleLength = visibleLength;
        this.activeTrailLength = activeTrailLength;
        this.beamAlpha = beamAlpha;
        this.arcAlpha = arcAlpha;
        this.trailAlpha = trailAlpha;
        this.originWorld = originWorld;
        this.direction = direction;
        this.cameraWorld = cameraWorld;
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
            beamAlpha, arcAlpha, trailAlpha, originWorld, direction, cameraWorld);
    }

    /** 主束与拖尾完全淡出后整帧可跳过。 */
    public boolean isExpired() {
        return trailAlpha <= 0.001f;
    }
}

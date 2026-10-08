package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import net.minecraft.world.phys.Vec3;

/** Shared arc geometry for blade visuals, ring rendering/particles and server-side hit checks. */
public final class FlameRingGeometry {
    public static final double HEIGHT_OFFSET = 1.05;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_X = new Vec3(1, 0, 0);

    private FlameRingGeometry() {}

    public static Vec3 forward(float lookPitch, float lookYaw) {
        return Vec3.directionFromRotation(lookPitch, lookYaw).normalize();
    }

    public static Vec3 right(Vec3 forward) {
        Vec3 right = forward.cross(WORLD_UP);
        if (right.lengthSqr() < 1e-4) right = forward.cross(WORLD_X);
        return right.normalize();
    }

    /** Convert the legacy horizontal start angle to the look-relative arc plane. */
    public static float relativeStartAngle(float startAngle, Vec3 forward) {
        return startAngle - (float) Math.toDegrees(Math.atan2(forward.z, forward.x));
    }

    public static Vec3 pointOnArc(Vec3 center, Vec3 forward, Vec3 right,
                                  float thetaDeg, double radius) {
        double rad = Math.toRadians(thetaDeg);
        return center.add(forward.scale(Math.cos(rad) * radius))
            .add(right.scale(Math.sin(rad) * radius));
    }

    public static Vec3 radial(Vec3 forward, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return forward.scale(Math.cos(rad)).add(right.scale(Math.sin(rad))).normalize();
    }

    public static Vec3 tangent(Vec3 forward, Vec3 right, float thetaDeg) {
        double rad = Math.toRadians(thetaDeg);
        return forward.scale(-Math.sin(rad)).add(right.scale(Math.cos(rad))).normalize();
    }
}

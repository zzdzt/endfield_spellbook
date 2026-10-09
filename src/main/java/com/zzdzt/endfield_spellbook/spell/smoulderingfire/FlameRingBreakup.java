package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

/**
 * Deterministic shared breakup plan for the Flame Ring mesh and its client particles.
 *
 * <p>This class intentionally has no client-only dependencies so both entity particle
 * choreography and the client renderer can derive the same fragment timing and motion.
 * The overall spell timeline remains owned by {@link FlameRingCastCurve}.</p>
 */
public final class FlameRingBreakup {
    /** Number of coherent arc fragments used by both geometry and particle choreography. */
    public static final int FRAGMENT_COUNT = 32;

    /** Once a shard reaches this progress, its original ring section is removed. */
    public static final float RING_RELEASE_PROGRESS = 0.30f;

    private static final float BREAK_THRESHOLD_MIN = 0.12f;
    private static final float BREAK_THRESHOLD_SPAN = 0.68f;

    private FlameRingBreakup() {
    }

    /** Stable per-fragment threshold, spread across the five-tick dissolve window. */
    public static float threshold(int fragment, int entityId) {
        return BREAK_THRESHOLD_MIN + BREAK_THRESHOLD_SPAN * hash01(fragment, 0, entityId);
    }

    /**
     * Per-fragment normalized lifecycle: 0 before release starts, 1 at the end of
     * the global breakup window. It is stable across frames and shared by mesh/particles.
     */
    public static float progress(float breakup, int fragment, int entityId) {
        float start = threshold(fragment, entityId);
        return clamp((breakup - start) / (1.0f - start), 0.0f, 1.0f);
    }

    /** Smooth acceleration from an attached shard to a flying streak. */
    public static float travel(float progress) {
        return smoothstep(0.04f, 0.72f, progress);
    }

    /** Tangential travel distance in world units. */
    public static float tangentialDistance(float progress, int fragment, int entityId) {
        return travel(progress) * (1.35f + 0.90f * hash01(fragment, 1, entityId));
    }

    /** Small radial escape from the original ring. */
    public static float radialDistance(float progress, int fragment, int entityId) {
        return travel(progress) * (hash01(fragment, 2, entityId) - 0.5f) * 0.90f;
    }

    /** Small depth escape so the streaks retain the existing volumetric read. */
    public static float normalDistance(float progress, int fragment, int entityId) {
        return travel(progress) * (hash01(fragment, 3, entityId) - 0.5f) * 0.36f;
    }

    /** Tangential streaks grow from arc fragments instead of appearing at full length. */
    public static float lengthScale(float progress) {
        return 1.0f + 2.25f * travel(progress);
    }

    /** Streaks narrow as their energy is stretched out. */
    public static float widthScale(float progress) {
        return 1.0f - 0.48f * travel(progress);
    }

    /** Fade only in the last part of each shard's own lifecycle (reference frames 14-16). */
    public static float opacity(float progress) {
        float fadeIn = smoothstep(0.01f, 0.16f, progress);
        float fadeOut = 1.0f - smoothstep(0.68f, 1.0f, progress);
        return fadeIn * fadeOut;
    }

    /** Keep the ring bright at the start, then ease its remaining body away. */
    public static float ringOpacity(float breakup) {
        return 1.0f - smoothstep(0.25f, 1.0f, breakup);
    }

    /** Stable normalized pseudo-random value; no per-frame random state. */
    public static float hash01(int fragment, int channel, int entityId) {
        long n = 0x9E3779B97F4A7C15L;
        n ^= (long) fragment * 0xBF58476D1CE4E5B9L;
        n ^= (long) channel * 0x94D049BB133111EBL;
        n ^= (long) entityId * 0xD6E8FEB86659FD93L;
        n ^= (n >>> 30);
        n *= 0xBF58476D1CE4E5B9L;
        n ^= (n >>> 27);
        n *= 0x94D049BB133111EBL;
        n ^= (n >>> 31);
        return (n & 0xFFFFFFL) / (float) 0x1000000;
    }

    public static int fragmentForNormalizedArc(float normalizedArc) {
        float clamped = clamp(normalizedArc, 0.0f, 0.999999f);
        return Math.min(FRAGMENT_COUNT - 1, (int) (clamped * FRAGMENT_COUNT));
    }

    public static float fragmentCenter(int fragment) {
        return (fragment + 0.5f) / FRAGMENT_COUNT;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}

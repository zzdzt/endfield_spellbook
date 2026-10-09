package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative hit controller for the V2 flame ring.
 * It shares the visible ring's timeline and checks only each newly revealed arc segment.
 */
public class FlameRingAttackEntity extends Entity implements SpellVisualOnly {
    private static final double SAMPLE_SPACING = 0.22;
    private static final double HIT_THICKNESS = 0.48;

    private LivingEntity caster;
    private DamageSource damageSource;
    private float damage;
    private Vec3 anchor = Vec3.ZERO;
    private float radius = 3.5f;
    private float startAngle;
    private float sweepAngle = 80f;
    private float lookPitch;
    private float lookYaw;
    private final Set<UUID> hitTargets = new HashSet<>();

    public FlameRingAttackEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public FlameRingAttackEntity(EntityType<?> type, Level level, LivingEntity caster,
                                 DamageSource damageSource, float damage, Vec3 anchor,
                                 float radius, float startAngle, float sweepAngle,
                                 float lookPitch, float lookYaw) {
        this(type, level);
        this.caster = caster;
        this.damageSource = damageSource;
        this.damage = damage;
        this.anchor = anchor;
        this.radius = radius;
        this.startAngle = startAngle;
        this.sweepAngle = sweepAngle;
        this.lookPitch = lookPitch;
        this.lookYaw = lookYaw;
        this.setPos(anchor.x, anchor.y, anchor.z);
    }

    @Override
    public void tick() {
        this.baseTick();
        if (level().isClientSide) return;
        if (caster == null || caster.isRemoved() || damageSource == null) {
            discard();
            return;
        }

        int age = this.tickCount;
        float previousDegrees = FlameRingCastCurve.revealedDegrees(
            Math.max(0, age - 1), Math.abs(sweepAngle));
        float currentDegrees = FlameRingCastCurve.revealedDegrees(age, Math.abs(sweepAngle));

        if (currentDegrees > previousDegrees + 1.0e-4f) {
            hitNewArc(previousDegrees, currentDegrees);
        }

        // Tick 11 includes the final propagation segment; burn and breakup remain visual-only.
        if (age >= FlameRingCastCurve.SLASH_TICKS + FlameRingCastCurve.PROPAGATE_TICKS) {
            discard();
        }
    }

    private void hitNewArc(float fromDegrees, float toDegrees) {
        Vec3 center = anchor.add(0, FlameRingGeometry.HEIGHT_OFFSET, 0);
        Vec3 forward = FlameRingGeometry.forward(lookPitch, lookYaw);
        Vec3 right = FlameRingGeometry.right(forward);
        float theta0 = FlameRingGeometry.relativeStartAngle(startAngle, forward);
        float direction = Math.signum(sweepAngle);
        if (direction == 0f) direction = 1f;

        double searchRadius = radius + HIT_THICKNESS;
        AABB searchBounds = new AABB(
            center.x - searchRadius, center.y - searchRadius, center.z - searchRadius,
            center.x + searchRadius, center.y + searchRadius, center.z + searchRadius
        );

        for (Entity candidate : level().getEntities(caster, searchBounds,
            entity -> entity instanceof LivingEntity living
                && living.isAlive()
                && living.isPickable()
                && !hitTargets.contains(living.getUUID()))) {
            if (!(candidate instanceof LivingEntity target)) continue;
            if (!Utils.hasLineOfSight(level(), caster.getEyePosition(),
                target.getBoundingBox().getCenter(), true)) continue;
            if (!touchesArc(target.getBoundingBox(), center, forward, right, theta0,
                direction, fromDegrees, toDegrees)) continue;

            // Register before applying damage so overlapping samples cannot double-hit this cast.
            if (!hitTargets.add(target.getUUID())) continue;
            if (DamageSources.applyDamage(target, damage, damageSource)) {
                MagicManager.spawnParticles(level(), ParticleHelper.FIRE,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.5f,
                    target.getZ(),
                    30,
                    target.getBbWidth() * 0.5f,
                    target.getBbHeight() * 0.5f,
                    target.getBbWidth() * 0.5f,
                    0.03, false);
                EnchantmentHelper.doPostDamageEffects(caster, target);
            }
        }
    }

    private boolean touchesArc(AABB targetBounds, Vec3 center, Vec3 forward, Vec3 right,
                               float theta0, float direction, float fromDegrees, float toDegrees) {
        double arcLength = radius * Math.toRadians(Math.abs(toDegrees - fromDegrees));
        int segments = Math.max(1, (int) Math.ceil(arcLength / SAMPLE_SPACING));
        AABB expandedTarget = targetBounds.inflate(HIT_THICKNESS);
        Vec3 previousPoint = FlameRingGeometry.pointOnArc(
            center, forward, right, theta0 + direction * fromDegrees, radius);
        for (int i = 1; i <= segments; i++) {
            float progress = i / (float) segments;
            float degree = fromDegrees + (toDegrees - fromDegrees) * progress;
            Vec3 nextPoint = FlameRingGeometry.pointOnArc(
                center, forward, right, theta0 + direction * degree, radius);
            if (expandedTarget.clip(previousPoint, nextPoint).isPresent()) return true;
            previousPoint = nextPoint;
        }
        return false;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

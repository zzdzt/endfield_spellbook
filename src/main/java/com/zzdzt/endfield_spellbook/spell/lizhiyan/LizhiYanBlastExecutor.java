package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Resolves and executes LizhiYan's coordinated blast after the attack controller
 * has decided the firing tick. Attack timing and phase transitions remain elsewhere.
 */
final class LizhiYanBlastExecutor {
    private LizhiYanBlastExecutor() {}

    static void fire(
            LizhiYanEntity sword,
            LivingEntity owner,
            float range,
            float[] tint,
            float damage,
            @Nullable Entity currentTarget,
            Vec3 fallbackDirection) {
        Vec3 lookDir;
        if (currentTarget != null && currentTarget.isAlive()) {
            Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight() / 2, 0);
            lookDir = targetCenter.subtract(sword.position()).normalize();
        } else {
            lookDir = fallbackDirection;
            if (lookDir.lengthSqr() < 0.0001) {
                lookDir = Vec3.directionFromRotation(sword.getXRot(), sword.getYRot());
            }
        }

        Vec3 startPos = sword.position().add(0, 0.3, 0);
        if (lookDir.lengthSqr() > 1e-6) sword.faceDirectionForBlast(lookDir);
        Vec3 endPos = startPos.add(lookDir.scale(range));
        var blockHit = sword.level().clip(new ClipContext(
                startPos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sword));
        Vec3 hitLoc = blockHit.getType() == HitResult.Type.MISS
                ? endPos : blockHit.getLocation();

        LizhiYanBlastVisualEntity blastEntity = new LizhiYanBlastVisualEntity(
                EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
                sword.level(), startPos, hitLoc, lookDir, tint);
        sword.level().addFreshEntity(blastEntity);

        LivingEntity hitEntity = findEntityOnPath(sword, owner, startPos, hitLoc, currentTarget);
        if (hitEntity != null) {
            var spell = SpellRegistry.LIZHI_YAN.get();
            SpellDamageSource source = SpellDamageSource.source(sword, owner, spell).setIFrames(0);
            DamageSources.applyDamage(hitEntity, damage, source);
        }

        sword.level().playSound(null, sword.getX(), sword.getY(), sword.getZ(),
                SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @Nullable
    private static LivingEntity findEntityOnPath(
            LizhiYanEntity sword,
            LivingEntity owner,
            Vec3 start,
            Vec3 end,
            @Nullable Entity currentTarget) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        AABB scanBox = new AABB(start, end).inflate(1.0);
        var entities = sword.level().getEntities(sword, scanBox, e -> {
            if (e == sword || e == owner) return false;
            if (!(e instanceof LivingEntity living)) return false;
            if (e instanceof Player player
                    && (currentTarget == null || !player.getUUID().equals(currentTarget.getUUID()))) {
                return false;
            }
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        for (Entity entity : entities) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> intercept = entityBox.clip(start, end);
            if (intercept.isPresent()) {
                double dist = start.distanceTo(intercept.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = (LivingEntity) entity;
                }
            }
        }
        return closest;
    }
}

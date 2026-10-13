package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 管理理之言飞剑的目标获取、保留与丢失视线判定。
 *
 * <p>这里只维护目标选择状态，不推进攻击相位，也不处理伤害。
 * {@link LizhiYanEntity} 仍是飞剑行为状态机的唯一编排者。</p>
 */
final class LizhiYanTargeting {
    private static final int LOS_CHECK_INTERVAL = 2;
    private static final int LOST_SIGHT_TIMEOUT = 13;
    private static final int SCAN_INTERVAL_NO_TARGET = 10;
    private static final int SCAN_INTERVAL_HAS_TARGET = 20;

    private final LizhiYanEntity sword;

    @Nullable
    private Entity currentTarget;
    @Nullable
    private Entity pendingTarget;
    private int lostSightTick;

    LizhiYanTargeting(LizhiYanEntity sword) {
        this.sword = sword;
    }

    @Nullable
    Entity getCurrentTarget() {
        return currentTarget;
    }

    @Nullable
    Entity getPendingTarget() {
        return pendingTarget;
    }

    void setCurrentTarget(@Nullable Entity target) {
        currentTarget = target;
    }

    void setPendingTarget(@Nullable Entity target) {
        pendingTarget = target;
    }

    void resetLostSightTick() {
        lostSightTick = 0;
    }

    /**
     * 优先采用玩家最近攻击的目标；否则保留当前有效目标，必要时降频扫描敌对生物。
     * 原实体类中的检查顺序与 tick 阈值在此次提取中保持不变。
     */
    void update(LivingEntity owner, double range) {
        Entity playerTarget = getPlayerCurrentTarget(owner);

        if (playerTarget != null && playerTarget.isAlive()) {
            UUID currentUUID = currentTarget != null ? currentTarget.getUUID() : null;
            UUID bestUUID = playerTarget.getUUID();

            if (!bestUUID.equals(currentUUID)) {
                boolean inRange = sword.distanceTo(playerTarget) <= range;
                boolean hasLos = hasLineOfSight(playerTarget);
                if (inRange && hasLos) {
                    pendingTarget = playerTarget;
                }
            }
        }

        if (pendingTarget == null && currentTarget != null) {
            if (!currentTarget.isAlive() || currentTarget.isRemoved()) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (sword.distanceTo(currentTarget) > range * 1.5) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (sword.tickCount % LOS_CHECK_INTERVAL == 0 && !hasLineOfSight(currentTarget)) {
                lostSightTick++;
                if (lostSightTick >= LOST_SIGHT_TIMEOUT) {
                    currentTarget = null;
                    lostSightTick = 0;
                }
            } else if (sword.tickCount % LOS_CHECK_INTERVAL == 0) {
                lostSightTick = 0;
            }
        }

        if (currentTarget == null && pendingTarget == null) {
            int scanInterval = (currentTarget == null && pendingTarget == null)
                ? SCAN_INTERVAL_NO_TARGET
                : SCAN_INTERVAL_HAS_TARGET;
            if (sword.tickCount % scanInterval == 0) {
                currentTarget = findNearestEnemy(owner, range);
            }
        }

        // 初次释放阶段由整组飞剑共享一个目标，避免各剑按自身位置独立扫描后错开攻击。
        synchronizeInitialSquadTarget();
    }


    /**
     * 在初始索敌窗口内，将同一施法者的飞剑统一到一个目标。
     * 玩家最近攻击目标（pendingTarget）优先于独立扫描结果；
     * 同类候选按 slotIndex 排序，确保所有实体看到同一结果。
     */
    private void synchronizeInitialSquadTarget() {
        if (!sword.isInitialTargetAcquisitionWindow()) return;

        var group = new java.util.ArrayList<>(sword.getSiblingSwordsForTargeting());
        group.add(sword);
        group.sort(java.util.Comparator.comparingInt(LizhiYanEntity::getSlotIndex));

        Entity sharedPending = null;
        Entity sharedCurrent = null;
        for (LizhiYanEntity member : group) {
            if (!member.isInitialTargetAcquisitionWindow()) continue;
            LizhiYanTargeting memberTargeting = member.getTargetingForCoordination();
            Entity pending = memberTargeting.pendingTarget;
            if (isValidTarget(pending)) {
                sharedPending = pending;
                break;
            }
        }

        if (sharedPending == null) {
            for (LizhiYanEntity member : group) {
                if (!member.isInitialTargetAcquisitionWindow()) continue;
                LizhiYanTargeting memberTargeting = member.getTargetingForCoordination();
                Entity current = memberTargeting.currentTarget;
                if (isValidTarget(current)) {
                    sharedCurrent = current;
                    break;
                }
            }
        }

        Entity shared = sharedPending != null ? sharedPending : sharedCurrent;
        if (shared == null) return;

        // 初始目标确定后统一为 currentTarget，让三剑同一轮进入 CHARGE。
        for (LizhiYanEntity member : group) {
            if (!member.isInitialTargetAcquisitionWindow()) continue;
            LizhiYanTargeting memberTargeting = member.getTargetingForCoordination();
            memberTargeting.currentTarget = shared;
            memberTargeting.pendingTarget = null;
            memberTargeting.lostSightTick = 0;
        }
    }

    private boolean isValidTarget(@Nullable Entity target) {
        return target instanceof LivingEntity && target.isAlive() && !target.isRemoved();
    }

    @Nullable
    private Entity getPlayerCurrentTarget(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer serverPlayer)) return null;

        LivingEntity lastHurt = serverPlayer.getLastHurtMob();
        if (lastHurt != null && lastHurt.isAlive()) {
            int lastHurtTimestamp = serverPlayer.getLastHurtMobTimestamp();
            if (serverPlayer.tickCount - lastHurtTimestamp < 100) {
                return lastHurt;
            }
        }
        return null;
    }

    @Nullable
    private Entity findNearestEnemy(LivingEntity owner, double range) {
        AABB aabb = new AABB(
            sword.getX() - range, sword.getY() - range, sword.getZ() - range,
            sword.getX() + range, sword.getY() + range, sword.getZ() + range
        );
        var candidates = sword.level().getEntities(owner, aabb, entity -> {
            if (entity == sword || entity == owner) return false;
            if (!(entity instanceof LivingEntity living)) return false;
            if (entity instanceof net.minecraft.world.entity.player.Player) return false;
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        return candidates.stream()
            .filter(this::hasLineOfSight)
            .min((a, b) -> Double.compare(sword.distanceTo(a), sword.distanceTo(b)))
            .orElse(null);
    }

    private boolean hasLineOfSight(Entity target) {
        Vec3 start = sword.position().add(0, 0.3, 0);
        Vec3 end = target.position().add(0, target.getBbHeight() / 2, 0);
        return sword.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sword
        )).getType() == HitResult.Type.MISS;
    }
}

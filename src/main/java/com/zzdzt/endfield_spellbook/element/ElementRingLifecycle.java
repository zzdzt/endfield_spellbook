package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * 纯视觉弧环不存档；附着目标重新加载/换维度时，根据持久化附着数据恢复弧环。
 *
 * <p>EntityJoinLevelEvent 可能早于区块完全加载，因此只入队，不在加入事件中创建实体；
 * 实际恢复延迟到对应 ServerLevel 的 END tick，避免在实体加入回调中嵌套加载/生成实体。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class ElementRingLifecycle {
    /** 所有访问均在服务端事件主线程；按 Level 实例分队列，避免扫描其他维度的待处理项。 */
    private static final Map<ServerLevel, Queue<UUID>> PENDING = new IdentityHashMap<>();

    private ElementRingLifecycle() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(event.getEntity() instanceof LivingEntity target)
            || target instanceof ServerPlayer
            || !target.isAlive()) {
            return;
        }

        // 没有附着数据也没有旧回链的普通实体无需排队。
        boolean hasRingReference = target.getPersistentData().hasUUID(ElementRingController.RING_ENTITY_UUID);
        boolean hasValidInfliction = ElementInflictionStore.isValid(target, level.getGameTime());
        if (hasRingReference || hasValidInfliction) {
            PENDING.computeIfAbsent(level, ignored -> new ArrayDeque<>()).add(target.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        Queue<UUID> pending = PENDING.remove(level);
        if (pending == null) {
            return;
        }

        UUID targetId;
        while ((targetId = pending.poll()) != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity target
                && target.isAlive()
                && !target.isRemoved()) {
                ElementRingController.refreshRing(target, level.getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level);
        }
    }
}

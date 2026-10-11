package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集中维护玩家战斗时间戳。
 *
 * <p>资源本身不再各自保存“上次是否在战斗”的状态；伤害事件只更新一份共享战斗状态，
 * 并在刷新时间戳前让已初始化资源结算到本次战斗开始时刻。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class PlayerChargeCombatTracker {
    private static final Map<UUID, Long> LAST_COMBAT_TICK = new ConcurrentHashMap<>();

    private PlayerChargeCombatTracker() {
    }

    /** 统一使用主世界 gameTime，避免切换维度后资源计时基准跳变。 */
    public static long gameTime(Entity entity) {
        if (entity instanceof ServerPlayer player && player.getServer() != null) {
            return player.getServer().overworld().getGameTime();
        }
        return entity.level().getGameTime();
    }

    public static boolean inCombat(Entity entity) {
        return inCombatAt(entity, gameTime(entity));
    }

    private static boolean inCombatAt(Entity entity, long now) {
        Long last = LAST_COMBAT_TICK.get(entity.getUUID());
        return last != null && now >= last && now - last < PlayerCharges.COMBAT_TIMEOUT_TICKS;
    }

    /** 上次造成或承受伤害的统一时间戳；没有记录时返回 null。 */
    public static Long lastCombatTick(Entity entity) {
        return LAST_COMBAT_TICK.get(entity.getUUID());
    }

    /** 登录/登出生命周期调用，避免把离线时间误认为在线战斗时间。 */
    public static void clear(Entity entity) {
        LAST_COMBAT_TICK.remove(entity.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) {
            return;
        }

        long now;
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer attacker = sourceEntity instanceof ServerPlayer attackerPlayer ? attackerPlayer : null;
        ServerPlayer victim = event.getEntity() instanceof ServerPlayer victimPlayer ? victimPlayer : null;

        if (attacker == null && victim == null) {
            return;
        }
        now = gameTime(attacker != null ? attacker : victim);

        if (attacker != null) {
            recordCombatActivity(attacker, now);
        }
        if (victim != null && (attacker == null || !victim.getUUID().equals(attacker.getUUID()))) {
            recordCombatActivity(victim, now);
        }
    }

    private static void recordCombatActivity(ServerPlayer player, long now) {
        boolean wasInCombat = inCombatAt(player, now);
        // 先按旧战斗状态结算，再刷新时间戳；否则本次伤害前的有效脱战时间会丢失。
        PlayerCharges.onCombatActivity(player, now, wasInCombat);
        LAST_COMBAT_TICK.put(player.getUUID(), now);
    }
}

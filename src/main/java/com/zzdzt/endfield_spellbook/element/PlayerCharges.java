package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家资源层 API 门面（persistentData 承载，Forge 1.20.1 无 Data Attachment）。
 *
 * <p>数据键按资源类型隔离：{@code <modid>:charge_<NAME>_stacks}（层数）、
 * {@code _last_regen}（回充计时基准）。死亡清空：玩家死亡重生为新实体，
 * persistentData 自然为空——无需事件清理。
 *
 * <p><b>tick 策略</b>（服务端玩家 tick 驱动）：
 * <ul>
 *   <li>OUT_OF_COMBAT_REGEN：非战斗时按 {@code regenIntervalTicks} 逐层回充至满</li>
 *   <li>BATTLE_START_SET：非战斗 → 战斗的转换沿触发重置为初始值</li>
 * </ul>
 *
 * <p><b>战斗状态判定</b>：1.20.1 的 CombatTracker 无公开 inCombat()——自建：
 * 玩家造成或承受伤害时刷新战斗时间戳，{@link #COMBAT_TIMEOUT_TICKS} 内视为战斗中。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class PlayerCharges {

    private PlayerCharges() {
    }

    /** 数值占位（待定）：最后一次造成/承受伤害后多久算脱离战斗。 */
    public static final int COMBAT_TIMEOUT_TICKS = 100;

    /** 战斗时间戳（玩家造成或承受伤害时刷新）。 */
    private static final Map<UUID, Long> LAST_COMBAT_TICK = new ConcurrentHashMap<>();

    /** 该玩家当前是否处于战斗状态。 */
    public static boolean inCombat(Entity player) {
        Long last = LAST_COMBAT_TICK.get(player.getUUID());
        return last != null && player.level().getGameTime() - last < COMBAT_TIMEOUT_TICKS;
    }

    // ==================== API ====================

    /** 增加层数（溢出截断到上限）。熔火满层时触发满层演出与抗性增益。 */
    public static void gain(Entity player, PlayerChargeType type, int amount) {
        if (amount <= 0) return;
        CompoundTag data = player.getPersistentData();
        int stacks = Math.min(type.getMaxStacks(), getStacks(data, type) + amount);
        data.putInt(key(type, "_stacks"), stacks);
        syncMark(player, type, stacks);

        if (type == PlayerChargeType.MOLTEN_FIRE
            && stacks >= type.getMaxStacks()
            && player instanceof LivingEntity living) {
            onMoltenFireFull(living);
        }
    }

    /** 当前层数。 */
    public static int peek(Entity player, PlayerChargeType type) {
        return getStacks(player.getPersistentData(), type);
    }

    /** 是否达到上限。 */
    public static boolean isFull(Entity player, PlayerChargeType type) {
        return peek(player, type) >= type.getMaxStacks();
    }

    /** 消耗全部层数，返回消耗数量（判定"满 N 层触发"用：先 {@link #isFull} 再消耗）。 */
    public static int consumeAll(Entity player, PlayerChargeType type) {
        CompoundTag data = player.getPersistentData();
        int stacks = getStacks(data, type);
        data.putInt(key(type, "_stacks"), 0);
        syncMark(player, type, 0);
        return stacks;
    }

    /** 清空该资源。 */
    public static void clear(Entity player, PlayerChargeType type) {
        player.getPersistentData().putInt(key(type, "_stacks"), 0);
        syncMark(player, type, 0);
    }

    /**
     * 层数 → 视觉标记效果（MobEffect 自动同步客户端供 HUD 读取；计数业务仍在 persistentData）。
     * duration = 层数 × 1 秒 + 缓冲 —— 效果图标下的时间条长度即近似层数比例（满层 = 满条）。
     * 目前只有熔火有玩家侧视觉。
     */
    private static void syncMark(Entity player, PlayerChargeType type, int stacks) {
        if (type != PlayerChargeType.MOLTEN_FIRE) return;
        if (!(player instanceof LivingEntity living)) return;
        var effect = EffectRegistry.MOLTEN_FIRE_MARK.get();
        if (stacks > 0) {
            living.addEffect(new MobEffectInstance(effect, stacks * 20 + 25, stacks - 1, true, false));
        } else {
            living.removeEffect(effect);
        }
    }

    // ==================== 战斗状态维护 ====================

    /**
     * 熔火满层反馈（服务端）：视线前方橙红迸发（第一人称可见）+ 抗性提升 V ×5 秒。
     */
    private static void onMoltenFireFull(LivingEntity player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // 满层演出：眼睛前方 1.5 格爆开（第一人称正对可见）
        Vec3 pos = player.getEyePosition().add(player.getForward().scale(1.5));
        float[] c = com.zzdzt.endfield_spellbook.element.EndfieldElement.HEAT.particleColor();
        org.joml.Vector3f heat = new org.joml.Vector3f(c[0], c[1], c[2]);
        io.redspace.ironsspellbooks.capabilities.magic.MagicManager.spawnParticles(level,
            new io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions(heat, 1.0f),
            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0, true);
        io.redspace.ironsspellbooks.capabilities.magic.MagicManager.spawnParticles(level,
            new io.redspace.ironsspellbooks.particle.SparkParticleOptions(heat),
            pos.x, pos.y, pos.z, 20, 0.2f, 0.2f, 0.2f, 0.25f, true);
        level.playSound(null, pos.x, pos.y, pos.z,
            net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS,
            0.9f, 0.7f);

        // 抗性提升 V ×5 秒
        player.addEffect(new MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4));
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity actor = event.getSource().getEntity();
        long now = event.getEntity().level().getGameTime();
        // 造成伤害的玩家 与 承受伤害的玩家 都刷新战斗时间戳
        if (actor instanceof ServerPlayer attacker) {
            LAST_COMBAT_TICK.put(attacker.getUUID(), now);
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            LAST_COMBAT_TICK.put(victim.getUUID(), now);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_COMBAT_TICK.remove(event.getEntity().getUUID());
    }

    // ==================== tick 策略处理 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long gameTime = player.level().getGameTime();
        boolean combat = inCombat(player);

        for (PlayerChargeType type : PlayerChargeType.values()) {
            CompoundTag data = player.getPersistentData();
            String combatKey = key(type, "_was_in_combat");
            boolean wasInCombat = data.getBoolean(combatKey);

            // 熔火标记续期（层数 > 0 时周期刷新 duration，防效果到期消失；时间条随层数变化）
            if (type == PlayerChargeType.MOLTEN_FIRE && gameTime % 15 == 0) {
                int stacks = getStacks(data, type);
                if (stacks > 0 && player.getEffect(EffectRegistry.MOLTEN_FIRE_MARK.get()) == null) {
                    syncMark(player, type, stacks);
                }
            }

            switch (type.getPolicy()) {
                case OUT_OF_COMBAT_REGEN -> {
                    // 脱战回充：非战斗 + 未满 + 间隔到期 → +1 层
                    String regenKey = key(type, "_last_regen");
                    if (!combat && peek(player, type) < type.getMaxStacks()
                        && type.getRegenIntervalTicks() > 0
                        && gameTime - data.getLong(regenKey) >= type.getRegenIntervalTicks()) {
                        data.putLong(regenKey, gameTime);
                        gain(player, type, 1);
                    } else if (combat) {
                        data.putLong(regenKey, gameTime); // 战斗中重置计时
                    }
                }
                case BATTLE_START_SET -> {
                    // 非战斗 → 战斗的转换沿：重置为初始值
                    if (combat && !wasInCombat) {
                        data.putInt(key(type, "_stacks"), type.getBattleStartValue());
                    }
                }
                case NONE -> {
                }
            }
            // 记录战斗状态（转换沿判定基准）
            data.putBoolean(combatKey, combat);
        }
    }

    // ==================== 工具 ====================

    private static String key(PlayerChargeType type, String suffix) {
        return EndfieldSpellbook.MOD_ID + ":charge_" + type.name() + suffix;
    }

    private static int getStacks(CompoundTag data, PlayerChargeType type) {
        return data.getInt(key(type, "_stacks"));
    }
}

package com.zzdzt.endfield_spellbook.spell.windarrow;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风矢穿林的起跳与滞空控制。
 *
 * <p>起跳方式参考 ISS Ascension：施加一次向上冲量并同步给客户端，不授予创造飞行权限。
 * 浮空期间通过 WindArrowFloatEffect 将重力属性降为零；到达目标高度后停止垂直运动。
 * 状态严格绑定真实 ISS Recast 生命周期，并由 5 秒滑动期限兜底；次数耗尽、超时和异常退出都会清理效果。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class WindArrowFlightController {
    private static final ResourceLocation SPELL_ID =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "wind_arrow");

    private static final double ASCENT_HEIGHT = 7.0;
    private static final double ASCENT_IMPULSE_SCALE = 0.15;
    private static final int EFFECT_CLEANUP_BUFFER_TICKS = 2;
    private static final double ARRIVAL_EPSILON = 0.12;

    private static final Map<UUID, FlightState> ACTIVE = new ConcurrentHashMap<>();
    /**
     * 技能正常结束后，短暂管理玩家直到真正触地。
     * 每 Tick 清零坠落距离，防止从技能滞空高度落下时受到摔落伤害。
     */
    private static final Set<UUID> LANDING_PROTECTION = ConcurrentHashMap.newKeySet();

    private WindArrowFlightController() {
    }

    /**
     * 开始起跳或刷新现有浮空期限。此方法只在服务端法术施放流程中调用。
     */
    public static void startOrRefresh(ServerPlayer player, int lifetimeTicks) {
        if (!player.isAlive() || lifetimeTicks <= 0) {
            return;
        }

        long now = player.level().getGameTime();
        UUID playerId = player.getUUID();
        FlightState current = ACTIVE.get(playerId);
        // 再次启动技能时转交给新的浮空状态管理。
        LANDING_PROTECTION.remove(playerId);
        if (current != null) {
            current.expiresAt = now + lifetimeTicks;
            applyOrRefreshFloatEffect(player, lifetimeTicks);
            return;
        }

        double maxY = player.level().getMaxBuildHeight() - player.getBbHeight() - 1.0;
        double targetY = Math.max(player.getY(), Math.min(player.getY() + ASCENT_HEIGHT, maxY));
        FlightState state = new FlightState(targetY, now + lifetimeTicks);
        ACTIVE.put(playerId, state);

        // 用属性效果实现真正的零重力，不设置 Entity.noGravity，也不授予 mayfly/flying。
        applyOrRefreshFloatEffect(player, lifetimeTicks);

        // 参考 AscensionSpell：一次性向前上方冲量，后续不再每 Tick 强制向上推。
        Vec3 horizontalLook = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (horizontalLook.lengthSqr() > 1.0e-6) {
            horizontalLook = horizontalLook.normalize();
        } else {
            horizontalLook = Vec3.ZERO;
        }
        Vec3 impulse = horizontalLook.add(0.0, 5.0, 0.0).scale(ASCENT_IMPULSE_SCALE);
        Vec3 velocity = player.getDeltaMovement().add(impulse);
        setVelocityAndSync(player, velocity.x, velocity.y, velocity.z);
        player.hasImpulse = true;
    }

    private static void applyOrRefreshFloatEffect(ServerPlayer player, int lifetimeTicks) {
        // 留少量缓冲，保证控制器在 Recast 超时的同一阶段先移除属性效果，
        // 避免效果恰好先到期、玩家在清理事件前短暂恢复重力。
        player.addEffect(new MobEffectInstance(
            EffectRegistry.WIND_ARROW_FLOAT.get(),
            lifetimeTicks + EFFECT_CLEANUP_BUFFER_TICKS,
            0,
            false,
            false,
            false
        ));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        FlightState state = ACTIVE.get(playerId);
        if (state == null) {
            tickLandingProtection(player);
            return;
        }

        long now = player.level().getGameTime();
        if (!player.isAlive()
            || player.isRemoved()
            || player.isPassenger()
            || player.isSpectator()) {
            stop(player, false);
            return;
        }

        boolean recastStillActive = MagicData.getPlayerMagicData(player)
            .getPlayerRecasts().hasRecastForSpell(SPELL_ID.toString());
        if (now >= state.expiresAt || !recastStillActive) {
            // 次数耗尽或超时属于正常结束：恢复重力，但保护到安全触地为止。
            stop(player, true);
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double remainingHeight = state.targetY - player.getY();
        if (remainingHeight > ARRIVAL_EPSILON) {
            // 上升完全依靠首次冲量和零重力，不在这里连续补加向上速度。
            state.hovering = false;
            return;
        }

        // 到达目标高度后进入滞空：只在刚到达或垂直速度再次出现时同步归零。
        if (!state.hovering || Math.abs(velocity.y) > 0.03) {
            setVelocityAndSync(player, velocity.x, 0.0, velocity.z);
        } else if (velocity.y != 0.0) {
            player.setDeltaMovement(velocity.x, 0.0, velocity.z);
        }
        state.hovering = true;
    }

    /**
     * 技能正常结束后持续重置坠落距离，直到角色真正落地。
     * PlayerTick END 发生在本 Tick 移动/碰撞之后；由于每个空中 Tick 都清零，
     * 触地结算前最多只会累计一个 Tick 的下落距离，不足以触发摔落伤害。
     */
    private static void tickLandingProtection(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!LANDING_PROTECTION.contains(playerId)) {
            return;
        }

        if (!player.isAlive() || player.isRemoved() || player.isPassenger() || player.isSpectator()) {
            LANDING_PROTECTION.remove(playerId);
            return;
        }

        player.resetFallDistance();
        if (player.onGround()) {
            LANDING_PROTECTION.remove(playerId);
        }
    }

    /**
     * 落地碰撞可能在本 Tick END 重置坠落距离之前结算，因此额外在 Fall 事件里归零伤害倍率。
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && LANDING_PROTECTION.contains(player.getUUID())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        } else {
            UUID playerId = event.getEntity().getUUID();
            ACTIVE.remove(playerId);
            LANDING_PROTECTION.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    private static void setVelocityAndSync(ServerPlayer player, double x, double y, double z) {
        player.setDeltaMovement(x, y, z);
        if (player.connection != null) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private static void stop(ServerPlayer player, boolean protectUntilLanding) {
        UUID playerId = player.getUUID();
        FlightState state = ACTIVE.remove(playerId);
        if (state == null) {
            if (!protectUntilLanding) {
                LANDING_PROTECTION.remove(playerId);
            }
            return;
        }

        // 移除属性修饰器即可恢复原有重力；不改动玩家本来的 noGravity 或飞行权限。
        player.removeEffect(EffectRegistry.WIND_ARROW_FLOAT.get());

        if (protectUntilLanding
            && player.isAlive()
            && !player.isRemoved()
            && !player.isPassenger()
            && !player.isSpectator()
            && !player.onGround()) {
            LANDING_PROTECTION.add(playerId);
            // 结束点先清零一次；后续由 tickLandingProtection 维持到触地。
            player.resetFallDistance();
        } else {
            LANDING_PROTECTION.remove(playerId);
        }

        Vec3 velocity = player.getDeltaMovement();
        setVelocityAndSync(player, velocity.x, 0.0, velocity.z);
    }

    private static final class FlightState {
        private final double targetY;
        private boolean hovering;
        private long expiresAt;

        private FlightState(double targetY, long expiresAt) {
            this.targetY = targetY;
            this.expiresAt = expiresAt;
        }
    }
}

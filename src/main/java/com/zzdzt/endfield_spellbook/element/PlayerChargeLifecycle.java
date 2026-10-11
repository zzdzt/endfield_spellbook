package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** 处理资源回充时钟的登录/登出边界。 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class PlayerChargeLifecycle {

    private PlayerChargeLifecycle() {
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 必须先结算并保存在线进度，再清掉内存中的战斗时间戳。
            PlayerCharges.pauseForLogout(player);
            PlayerChargeCombatTracker.clear(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerChargeCombatTracker.clear(player);
            PlayerCharges.cleanupLegacyCombatState(player);
            // 恢复登出前不足一个周期的进度，但不把离线时间计入回充。
            PlayerCharges.resumeAfterLogin(player);
            MoltenFireFeedback.maintainMark(player);
        }
    }
}

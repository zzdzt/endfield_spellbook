package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import org.joml.Vector3f;

/** 熔火层数的 HUD 标记与满层反馈，与通用资源读写、回充逻辑解耦。 */
public final class MoltenFireFeedback {
    private static final int MARK_REFRESH_BUFFER = 15;
    private static final int FULL_REWARD_DURATION = 100;

    private MoltenFireFeedback() {
    }

    /** 层数变化时同步 HUD 标记；0 层时移除。 */
    static void syncMark(Entity entity, int stacks) {
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide) {
            return;
        }

        var effect = EffectRegistry.MOLTEN_FIRE_MARK.get();
        if (stacks > 0) {
            living.addEffect(new MobEffectInstance(
                effect,
                stacks * 20 + 25,
                stacks - 1,
                true,
                false
            ));
        } else {
            living.removeEffect(effect);
        }
    }

    /**
     * 只由熔火标记效果在即将到期时调用；无标记玩家不会参与轮询。
     * 在标记即将到期时补挂，避免到期后出现图标缺口。
     */
    public static void maintainMark(LivingEntity player) {
        var effectType = EffectRegistry.MOLTEN_FIRE_MARK.get();
        if (!PlayerCharges.isInitialized(player, PlayerChargeType.MOLTEN_FIRE)) {
            player.removeEffect(effectType);
            return;
        }

        int stacks = PlayerCharges.peek(player, PlayerChargeType.MOLTEN_FIRE);
        MobEffectInstance current = player.getEffect(effectType);

        if (stacks <= 0) {
            if (current != null) {
                player.removeEffect(effectType);
            }
            return;
        }

        if (current == null
            || current.getAmplifier() != stacks - 1
            || current.getDuration() <= MARK_REFRESH_BUFFER) {
            syncMark(player, stacks);
        }
    }

    /** 只在层数从未满跨越到满时调用，避免已满层后的每次 gain 重复触发。 */
    static void onBecameFull(LivingEntity player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 pos = player.getEyePosition().add(player.getForward().scale(1.5));
        float[] c = EndfieldElement.HEAT.particleColor();
        Vector3f heat = new Vector3f(c[0], c[1], c[2]);

        MagicManager.spawnParticles(level, new BlastwaveParticleOptions(heat, 1.0f),
            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level, new SparkParticleOptions(heat),
            pos.x, pos.y, pos.z, 20, 0.2f, 0.2f, 0.2f, 0.25f, true);
        level.playSound(null, pos.x, pos.y, pos.z,
            net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 0.7f);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
            FULL_REWARD_DURATION, 4));
    }
}

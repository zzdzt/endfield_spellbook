package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.effect.ArtsVulnerableEffect;
import com.zzdzt.endfield_spellbook.effect.ErodedEffect;
import com.zzdzt.endfield_spellbook.effect.HeatVulnerableEffect;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;

import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * 元素异常的伤害/治疗乘区结算（服务端事件）。
 *
 * <ul>
 *   <li><b>导电</b>：目标带导电效果且伤害来源为我们的法术 → 伤害 ×(1 + 易伤)</li>
 *   <li><b>腐蚀·降伤</b>：攻击者带腐蚀效果 → 其造成的伤害 ×(1 - 降伤比例)</li>
 *   <li><b>冻结窗口</b>：目标带冻结窗口 → 本次伤害 ×(1 + 增伤)，消耗 1 次，用完关闭</li>
 *   <li><b>腐蚀·降疗</b>：目标带腐蚀效果 → 受到的治疗 ×(1 - 降疗比例)</li>
 * </ul>
 *
 * <p>只干预元素异常相关乘区，不触碰铁魔法原版与原版伤害的其它行为。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class ElementReactionHandler {

    private ElementReactionHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (event.getAmount() <= 0) return;

        LivingEntity target = event.getEntity();
        var source = event.getSource();
        float amount = event.getAmount();

        // ① 导电：我们的法术伤害对带导电效果的目标增伤
        var conductive = target.getEffect(EffectRegistry.ARTS_VULNERABLE.get());
        if (conductive != null
            && source instanceof SpellDamageSource spellSource
            && spellSource.spell().getSpellResource().getNamespace().equals(EndfieldSpellbook.MOD_ID)) {
            amount *= 1.0f + ArtsVulnerableEffect.getAmplification(conductive.getAmplifier());
        }

        // ② 腐蚀·降伤：攻击者带腐蚀效果时，其造成的伤害降低
        if (source.getEntity() instanceof LivingEntity attacker) {
            var eroded = attacker.getEffect(EffectRegistry.ERODED.get());
            if (eroded != null) {
                amount *= 1.0f - ErodedEffect.getDamageReduction(eroded.getAmplifier());
            }
        }

        // ③ 冻结窗口：目标受击消耗 1 次次数，窗口用完关闭（同步移除视觉标记）
        if (FrozenWindowStore.isValid(target, level.getGameTime())) {
            amount *= 1.0f + FrozenWindowStore.getBonusPct(target);
            FrozenWindowStore.decrementHits(target);
            if (!FrozenWindowStore.isValid(target, level.getGameTime())) {
                target.removeEffect(EffectRegistry.FROZEN_MARK.get());
            }
        }

        // ④ 灼热脆弱：目标受到的灼热伤害提高（按伤害类型判定——我方火学派法术、
        //    元素爆发、燃烧 DoT 同为 FIRE_MAGIC，与燃烧的联动由此免费获得）
        if (target.hasEffect(EffectRegistry.HEAT_VULNERABLE.get())
            && source.is(EndfieldElement.HEAT.damageTypeKey())) {
            amount *= 1.0f + HeatVulnerableEffect.AMPLIFICATION;
        }

        event.setAmount(amount);
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // ⑤ 腐蚀·降疗：目标受到的治疗降低
        var eroded = target.getEffect(EffectRegistry.ERODED.get());
        if (eroded != null && event.getAmount() > 0) {
            event.setAmount(event.getAmount() * (1.0f - ErodedEffect.getHealReduction(eroded.getAmplifier())));
        }
    }
}

package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Executes direct hit damage for LizhiYan's thrust attack. */
final class LizhiYanCombatExecutor {
    private static final float[] STAB_DAMAGE_MULTIPLIERS = {1.0f, 1.3f};

    private LizhiYanCombatExecutor() {
    }

    static void applyStabDamage(LizhiYanEntity sword, LivingEntity target) {
        LivingEntity owner = sword.getOwner();
        if (owner == null) return;

        float multiplier = STAB_DAMAGE_MULTIPLIERS[
                Math.min(sword.getAttackIndex(), STAB_DAMAGE_MULTIPLIERS.length - 1)];
        float damage = sword.getBaseDamage() * multiplier;
        var spell = SpellRegistry.LIZHI_YAN.get();
        SpellDamageSource source = SpellDamageSource.source(sword, owner, spell).setIFrames(5);
        DamageSources.applyDamage(target, damage, source);
    }
}

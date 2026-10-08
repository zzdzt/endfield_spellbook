package com.zzdzt.endfield_spellbook.registry;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.spell.bloodwing.BloodwingSpell;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeSpell;
import com.zzdzt.endfield_spellbook.spell.jingtingjue.SunderbladeStrikeSpell;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LiquidNitrogenCannonSpell;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanSpell;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.SmoulderingFireSpell;
import com.zzdzt.endfield_spellbook.spell.test.TestApplyCryoSpell;
import com.zzdzt.endfield_spellbook.spell.test.TestApplyElectricSpell;
import com.zzdzt.endfield_spellbook.spell.test.TestApplyHeatSpell;
import com.zzdzt.endfield_spellbook.spell.test.TestApplyNatureSpell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class SpellRegistry {
    public static final DeferredRegister<AbstractSpell> SPELLS =
        DeferredRegister.create(
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.SPELL_REGISTRY_KEY,
            EndfieldSpellbook.MOD_ID
        );

    // ========== 焚灭法术 ==========
    public static final RegistryObject<AbstractSpell> SMOULDERING_FIRE =
        SPELLS.register("smouldering_fire", SmoulderingFireSpell::new);

    // ========== 离子岩飞剑法术 ==========
    public static final RegistryObject<AbstractSpell> LIZHI_YAN =
        SPELLS.register("lizhi_yan", LizhiYanSpell::new);

    // ========== 液氮大炮法术 ==========
    public static final RegistryObject<AbstractSpell> LIQUID_NITROGEN_CANNON =
        SPELLS.register("liquid_nitrogen_cannon", LiquidNitrogenCannonSpell::new);

    // ========== 破晦阵法术 ==========
    public static final RegistryObject<AbstractSpell> GLOOMPURGE =
        SPELLS.register("gloompurge", GloompurgeSpell::new);

    // ========== 元素附着测试法术（生产者接线验证用） ==========
    public static final RegistryObject<AbstractSpell> TEST_APPLY_HEAT =
        SPELLS.register("test_apply_heat", TestApplyHeatSpell::new);

    public static final RegistryObject<AbstractSpell> TEST_APPLY_ELECTRIC =
        SPELLS.register("test_apply_electric", TestApplyElectricSpell::new);

    public static final RegistryObject<AbstractSpell> TEST_APPLY_CRYO =
        SPELLS.register("test_apply_cryo", TestApplyCryoSpell::new);

    public static final RegistryObject<AbstractSpell> TEST_APPLY_NATURE =
        SPELLS.register("test_apply_nature", TestApplyNatureSpell::new);

    // ========== 驱火焚影 ==========
    public static final RegistryObject<AbstractSpell> BLOODWING =
        SPELLS.register("bloodwing", BloodwingSpell::new);

    // ========== 青霆剑诀 ==========
    public static final RegistryObject<AbstractSpell> SUNDERBLADE_STRIKE =
        SPELLS.register("sunderblade_strike", SunderbladeStrikeSpell::new);

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
}

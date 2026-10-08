package com.zzdzt.endfield_spellbook.spell.test;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;

/** 测试：施加 1 层电磁附着。 */
public class TestApplyElectricSpell extends ElementTestSpellBase {
    public TestApplyElectricSpell() {
        super(EndfieldElement.ELECTRIC, "test_apply_electric", SchoolRegistry.LIGHTNING_RESOURCE);
    }
}

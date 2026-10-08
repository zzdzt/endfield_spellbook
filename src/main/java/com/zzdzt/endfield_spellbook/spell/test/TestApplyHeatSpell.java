package com.zzdzt.endfield_spellbook.spell.test;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;

/** 测试：施加 1 层灼热附着。 */
public class TestApplyHeatSpell extends ElementTestSpellBase {
    public TestApplyHeatSpell() {
        super(EndfieldElement.HEAT, "test_apply_heat", SchoolRegistry.FIRE_RESOURCE);
    }
}

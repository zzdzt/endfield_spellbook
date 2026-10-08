package com.zzdzt.endfield_spellbook.spell.test;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;

/** 测试：施加 1 层自然附着。 */
public class TestApplyNatureSpell extends ElementTestSpellBase {
    public TestApplyNatureSpell() {
        super(EndfieldElement.NATURE, "test_apply_nature", SchoolRegistry.NATURE_RESOURCE);
    }
}

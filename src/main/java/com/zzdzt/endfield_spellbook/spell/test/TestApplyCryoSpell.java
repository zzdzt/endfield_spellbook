package com.zzdzt.endfield_spellbook.spell.test;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.resources.ResourceLocation;

/** 测试：施加 1 层寒冷附着。 */
public class TestApplyCryoSpell extends ElementTestSpellBase {
    public TestApplyCryoSpell() {
        super(EndfieldElement.CRYO, "test_apply_cryo", SchoolRegistry.ICE_RESOURCE);
    }
}

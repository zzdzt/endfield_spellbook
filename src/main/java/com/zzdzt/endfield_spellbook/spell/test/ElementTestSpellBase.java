package com.zzdzt.endfield_spellbook.spell.test;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

/**
 * 元素附着测试法术基类：视线锁定目标，施加 1 层对应元素附着。
 *
 * <p>瞬发、低耗、短冷却——纯测试用途，用于验证附着/爆发/反应机制。
 * 视线 32 格内未命中生物则提示失败。
 */
public abstract class ElementTestSpellBase extends AbstractSpell {

    private static final float TARGET_RANGE = 32.0f;

    private final ResourceLocation spellId;
    private final EndfieldElement element;

    protected ElementTestSpellBase(EndfieldElement element, String spellPath, ResourceLocation schoolResource) {
        this.spellId = ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, spellPath);
        this.element = element;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 0;
        this.castTime = 0;
        this.defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(schoolResource)
            .setMaxLevel(1)
            .setCooldownSeconds(1)
            .build();
    }

    private final DefaultConfig defaultConfig;

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".test.info.apply",
                Component.translatable("element." + EndfieldSpellbook.MOD_ID + "." + element.name().toLowerCase()))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData playerMagicData) {
        caster.swing(InteractionHand.MAIN_HAND, true);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            HitResult hit = Utils.raycastForEntity(level, caster, TARGET_RANGE, true);
            if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity target
                && target != caster) {
                // 生产者接线：施加 1 层对应元素附着（apply 内部处理新附着/叠层爆发/异元素反应）
                EndfieldElements.apply(caster, target, element);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 0.7F);
            } else if (caster instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("§7[测试] 视线范围内未命中生物目标"));
            }
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }
}

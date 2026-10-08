package com.zzdzt.endfield_spellbook.spell.gloompurge;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;

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
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * 破晦阵（Gloompurge）
 *
 * - INSTANT 施放，在施法点展开固定领域，
 * - 领域持续期间，范围内敌人受到施法者造成的任意伤害时，
 *   自目标斜上方随机方位（与地面 60°）降下 lizhi_yan 同款 blast 光束，
 *   即时单体结算
 * - 回声伤害 = 触发伤害 × ECHO_DAMAGE_PERCENT × 法术强度倍率
 * - 回声自身不触发连锁（防递归），每目标有短冷却防枪械射速刷屏
 *
 * 法术壳只负责展开领域与演出，触发逻辑在 GloompurgeEventHandler。
 */
public class GloompurgeSpell extends AbstractSpell {

    // 领域半径随等级：L1=14 → L5=22
    private static final float RADIUS_BASE = 14.0f;
    private static final float RADIUS_PER_LEVEL = 2.0f;
    // 回声伤害 = 触发伤害 × 该比例 × 属性法强倍率
    // 对齐 ISS 3.16.0 EchoingStrikes：系数不随等级，只由装备法强放大；
    // 等级收益转移到「领域时长」与「领域范围」两项
    public static final float ECHO_DAMAGE_PERCENT = 0.6f;
    // 每目标回声冷却（tick）
    public static final int ECHO_COOLDOWN_TICKS = 5;

    // 领域半径（格）：随等级线性增长
    public static float getRadius(int spellLevel) {
        return RADIUS_BASE + RADIUS_PER_LEVEL * (spellLevel - 1);
    }

    // 领域持续 tick：法术强度 × 20（对齐 ISS 3.16.0 EchoingStrikes 的 effect_length）
    // 无装备加成时 L1=20s → L5=40s；法强装备可进一步延长
    public static int getDurationTicks(int spellLevel, LivingEntity caster) {
        return (int) (SpellRegistry.GLOOMPURGE.get().getSpellPower(spellLevel, caster) * 20);
    }

    /** 实际回声比例：基准 0.6 × 施法者法术强度倍率（tooltip 显示含法强的实际值，对齐 EchoingStrikes 模式）。 */
    public static float getEchoDamagePercent(int spellLevel, LivingEntity caster) {
        return ECHO_DAMAGE_PERCENT * SpellRegistry.GLOOMPURGE.get().getEntityPowerMultiplier(caster);
    }

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "gloompurge"
    );

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
        .setMaxLevel(5)
        .setCooldownSeconds(100)
        .build();

    public GloompurgeSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 5;
        this.castTime = 0;
        this.baseManaCost = 50;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("ui.irons_spellbooks.radius",
                Utils.stringTruncation(getRadius(spellLevel), 1)),
            Component.translatable("ui.irons_spellbooks.duration",
                Utils.timeFromTicks(getDurationTicks(spellLevel, caster), 1)),
            Component.translatable("ui.irons_spellbooks.percent_damage",
                Utils.stringTruncation(getEchoDamagePercent(spellLevel, caster) * 100, 0))
        );
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.ENDER_CAST.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            GloompurgeEventHandler.openDomain(entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * 回声打击不设无敌帧：目标刚吃过触发伤害，若走默认 iFrames 回声会被吞掉
     */
    @Override
    public SpellDamageSource getDamageSource(Entity projectile, Entity attacker) {
        return super.getDamageSource(projectile, attacker).setIFrames(0);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }
}

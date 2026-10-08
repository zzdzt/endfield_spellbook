package com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * 液氮大炮（温蒂 S3 对齐）。
 *
 * 发射一颗压缩液氮炮弹：
 *   - 低平弹道，命中第一个敌人 / 方块 / 飞满射程后起爆
 *   - 以着弹点为圆心的圆形范围法术伤害 + 极强水平击退（受击退抗性衰减）
 *   - 命中者获得液氮标记：8 秒内移动时受到正比于移动距离的伤害
 *
 * 判定完全由 LncProjectileEntity 承担（弹道 + 圆形范围），本类只负责发射。
 */
public class LiquidNitrogenCannonSpell extends AbstractSpell {

    // 爆炸参数
    /** 爆炸半径（格）：原作 1.2 格圆，按 MC 战斗尺度放大 */
    public static final float EXPLOSION_RADIUS = 3.5f;
    /** 弹体最大射程（格） */
    public static final float MAX_RANGE = 14f;

    // 击退参数（原作"极强"）
    private static final float KNOCKBACK_BASE = 1.8f;
    // 击退抗性衰减范围：最低 10%，最高 100%
    private static final float KB_RESIST_MIN = 0.1f;
    private static final float KB_RESIST_MAX = 1.0f;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "liquid_nitrogen_cannon"
    );

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
        .setMaxLevel(3)
        .setCooldownSeconds(15)
        .build();

    public LiquidNitrogenCannonSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 5;
        this.castTime = 0;
        this.baseManaCost = 40;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("ui.irons_spellbooks.aoe_damage",
                Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
            Component.translatable("ui.irons_spellbooks.radius", EXPLOSION_RADIUS),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".liquid_nitrogen_cannon.info.knockback",
                Utils.stringTruncation(getKnockbackStrength(spellLevel, caster), 1))
        );
    }

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
    public Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    public float getKnockbackStrength(int spellLevel, LivingEntity caster) {
        return KNOCKBACK_BASE + getSpellPower(spellLevel, caster) * 0.05f;
    }

    // 核心施法逻辑：发射弹体，判定全部在弹体内进行

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            Vec3 forward = entity.getForward();
            // 炮口：眼位前移，避免弹体与施法者自身碰撞
            Vec3 muzzle = entity.getEyePosition().add(forward.scale(0.9));

            LncProjectileEntity projectile = new LncProjectileEntity(
                EntityRegistry.LNC_PROJECTILE.get(),
                level,
                entity,
                muzzle,
                forward,
                getSpellPower(spellLevel, entity),
                spellLevel,
                getEntityPowerMultiplier(entity),
                getKnockbackStrength(spellLevel, entity),
                EXPLOSION_RADIUS,
                MAX_RANGE
            );
            level.addFreshEntity(projectile);

            // 炮口喷发：压缩气体白雾 + 发射音（低沉"砰"）
            Vec3 sprayCenter = muzzle.add(forward.scale(0.6));
            MagicManager.spawnParticles(level, ParticleTypes.CLOUD,
                sprayCenter.x, sprayCenter.y, sprayCenter.z,
                5, 0.15f, 0.15f, 0.15f, 0.008, false);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.6f);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}

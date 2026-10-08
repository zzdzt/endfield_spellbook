package com.zzdzt.endfield_spellbook.spell.focus;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;

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
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 指定研究对象（终末地·安塔尔战技；原连携技「磁暴试验场」简化并入为自动补层）：
 * 1s 吟唱锁定目标，施加长时间「聚焦」标记（无伤害）。
 *
 * <p>规则（拍板）：
 * <ul>
 *   <li>聚焦持续时间随等级成长：10/15/20/25/30 秒（1~5 级）；被聚焦的敌人<b>每次</b>受到元素附着
 *       （新挂 / 同元素叠层 / 跨元素反应）时，自动以聚焦主人身份<b>延迟 10 tick</b> 再施加一层同元素
 *       （{@code EndfieldElements.applyNoNotify}：层数 +1，≥2 层自动法术爆发——
 *       爆发按主人法强结算、燃烧 DoT 记主人；不广播，防补层连锁）</li>
 *   <li>双唯一约束：每施法者同时只聚焦 1 个目标；每目标同时只被 1 名施法者聚焦。
 *       后者施法覆盖前者（"抢焦点"），补层归属随标记转移</li>
 *   <li>补层链止于一层：EndfieldElements 内 IN_APPLY 防重入，补层 apply 不再广播</li>
 *   <li>视觉：目标身体周期 ISS 电粒子（{@code ParticleHelper.ELECTRICITY}）+
 *       目标身上「聚焦」debuff（HUD）——不用地面几何环，
 *       避免与元素附着弧环混淆（2026-10-10 拍板）</li>
 * </ul>
 */
public class FocusSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "designated_subject");

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
        .setMaxLevel(5)
        .setCooldownSeconds(10)
        .build();

    /** 聚焦落点反馈：Blastwave 环半径（格）。 */
    private static final float CAST_BLAST_RADIUS = 0.8f;
    /** 聚焦落点反馈：电粒子数量。 */
    private static final int CAST_SPARK_COUNT = 10;
    /** 锁定范围/吸准（与青霆/血翼同参）。 */
    private static final int TARGET_RANGE = 32;
    private static final float TARGET_ANGLE = 0.35f;
    /** 覆盖扫描半径（标记跟随目标，血翼同款 64 格扫描盒）。 */
    private static final double OVERRIDE_SCAN_RANGE = 64;

    public FocusSpell() {
        // 纯标记无伤害：法强归零
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 25;
        this.manaCostPerLevel = 0;
        this.castTime = 20; // 1s 吟唱（LONG）
    }

    /** 聚焦持续时间（tick）：等级成长 10/15/20/25/30 秒（1~5 级）。 */
    public static int getDurationTicks(int spellLevel) {
        return (5 * spellLevel + 5) * 20;
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
        return CastType.LONG;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".info.duration",
                getDurationTicks(spellLevel) / 20),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".designated_subject.info.rule")
        );
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return Utils.preCastTargetHelper(level, entity, playerMagicData, this, TARGET_RANGE, TARGET_ANGLE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        LivingEntity target = getTargetFromCastData(playerMagicData, level);
        if (target == null || !target.isAlive() || !(level instanceof ServerLevel serverLevel)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        // 双唯一覆盖：同目标旧标记（任意主人，"抢焦点"）或自己的旧标记（任意目标，转移/刷新）
        level.getEntitiesOfClass(FocusMarkEntity.class,
                target.getBoundingBox().inflate(OVERRIDE_SCAN_RANGE),
                m -> !m.isRemoved() && (m.isFocusedOn(target) || m.isOwnedBy(entity)))
            .forEach(FocusMarkEntity::destroy);

        serverLevel.addFreshEntity(new FocusMarkEntity(
            EntityRegistry.FOCUS_MARK.get(), serverLevel, target, entity, getDurationTicks(spellLevel)));
        spawnCastFeedback(serverLevel, target);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 聚焦落点反馈：目标处电磁迸发（小 Blastwave 环 + 身体周围 ISS 电粒子）。 */
    private static void spawnCastFeedback(ServerLevel level, LivingEntity target) {
        float[] c = EndfieldElement.ELECTRIC.particleColor();
        Vector3f col = new Vector3f(c[0], c[1], c[2]);
        double bodyH = target.getBbHeight();
        MagicManager.spawnParticles(level, new BlastwaveParticleOptions(col, CAST_BLAST_RADIUS),
            target.getX(), target.getY() + 0.1, target.getZ(), 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level, ParticleHelper.ELECTRICITY,
            target.getX(), target.getY() + bodyH * 0.5, target.getZ(),
            CAST_SPARK_COUNT, 0.3, bodyH * 0.4, 0.3, 0.25, true);
    }

    @Nullable
    private LivingEntity getTargetFromCastData(MagicData magicData, Level level) {
        var castData = magicData.getAdditionalCastData();
        if (!(castData instanceof TargetEntityCastData targetData) || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity target = targetData.getTarget(serverLevel);
        return target instanceof LivingEntity livingTarget && livingTarget.isAlive() ? livingTarget : null;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }
}

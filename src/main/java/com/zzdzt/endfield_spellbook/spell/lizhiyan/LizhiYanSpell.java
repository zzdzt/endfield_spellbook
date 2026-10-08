package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * lizhiyan
 *
 * - 召唤3把浮游飞剑
 * - 飞剑自动寻敌
 * - 三段攻击循环：前两段戳刺，第三段发射冲击波
 */
public class LizhiYanSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "lizhi_yan"
    );

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(100)
            .build();

    public LizhiYanSpell() {
        this.manaCostPerLevel = 40;
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 2;
        this.castTime = 30;
        this.baseManaCost = 120;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("ui.irons_spellbooks.summon_count", 3),
            Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getBaseDamage(spellLevel, caster), 2)),
            Component.translatable("ui.irons_spellbooks.duration", 30)
        );
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
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
    public java.util.Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return java.util.Optional.of(SoundRegistry.SUMMONED_SWORDS_CHARGE.get());
    }

    @Override
    public java.util.Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return java.util.Optional.of(SoundRegistry.SUMMONED_SWORDS_CAST.get());
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return 2;
    }

    @Override
    public ICastDataSerializable getEmptyCastData() {
        return new SummonedEntitiesCastData();
    }

    @Override
    public void onRecastFinished(ServerPlayer serverPlayer, RecastInstance recastInstance,
                                 RecastResult recastResult, ICastDataSerializable castDataSerializable) {
        // 清理召唤物（超时或玩家主动消除时触发）
        if (SummonManager.recastFinishedHelper(serverPlayer, recastInstance, recastResult, castDataSerializable)) {
            super.onRecastFinished(serverPlayer, recastInstance, recastResult, castDataSerializable);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        PlayerRecasts recasts = playerMagicData.getPlayerRecasts();

        if (!recasts.hasRecastForSpell(this)) {
            // 首次施放：召唤飞剑 + 创建 RecastInstance 管理生命周期
            SummonedEntitiesCastData summonedData = new SummonedEntitiesCastData();
            int summonTime = 20 * 30; // 30秒
            float damage = getBaseDamage(spellLevel, entity);
            int count = 3;

            for (int i = 0; i < count; i++) {
                LizhiYanEntity sword = new LizhiYanEntity(
                    EntityRegistry.LIZHI_YAN.get(),
                    level,
                    entity,
                    i,
                    count
                );
                sword.setBaseDamage(damage);
                sword.setRange(20.0f);

                level.addFreshEntity(sword);
                SummonManager.initSummon(entity, sword, summonTime, summonedData);
            }

            RecastInstance recastInstance = new RecastInstance(
                getSpellId(),
                spellLevel,
                getRecastCount(spellLevel, entity),
                summonTime,
                castSource,
                summonedData
            );
            recasts.addRecast(recastInstance, playerMagicData);
        }
        // 第二次按下时：hasRecastForSpell=true → 不执行任何操作
        // castSpell() 内部 decrementRecastCount → remaining=0 → onRecastFinished → 消除飞剑

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private float getBaseDamage(int spellLevel, LivingEntity caster) {
        float base = getSpellPower(spellLevel, caster);
        float powerBoost = getEntityPowerMultiplier(caster);
        return base * (1.0f + (powerBoost - 1.0f) * 1.5f);
    }
}

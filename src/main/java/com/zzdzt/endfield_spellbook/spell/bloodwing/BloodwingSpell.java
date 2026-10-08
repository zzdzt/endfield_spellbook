package com.zzdzt.endfield_spellbook.spell.bloodwing;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * 驱火焚影（终末地·卡缪战技本地化）：
 * 召唤衔火血翼扑向目标——AoE 灼热伤害（附着与 debuff 仅主目标），随后盘桓施加虚弱+灼热脆弱；
 * 主目标倒下时转移下一个敌人（全套装）；再次施放（Recast）→ 血翼蓄势爆裂造成额外灼热伤害后收尾消散。
 *
 * <p>灼热脆弱与燃烧 DoT 联动：脆弱期间所有灼热伤害（含燃烧每跳、元素爆发、火学派法术）+20%，
 * 乘区在 {@code ElementReactionHandler} 按伤害类型统一结算。
 */
public class BloodwingSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "bloodwing");

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
        .setMaxLevel(10)
        .setCooldownSeconds(20)
        .build();

    public BloodwingSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 1;
        this.castTime = 15;
        this.baseManaCost = 40;
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
            Component.translatable("ui.irons_spellbooks.damage",
                Utils.stringTruncation(getSpellPower(spellLevel, caster) * BloodwingCastCurve.IMPACT_DAMAGE_FRACTION, 1)),
            Component.translatable("spell.endfield_spellbook.info.duration",
                BloodwingCastCurve.LIFETIME_TICKS / 20)
        );
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.FIRECHARGE_USE);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        // Recast 二段（爆裂收尾）不要求目标：血翼自带目标，且目标可能已离开视线。
        // ⚠️ ISS 门禁链 canBeCastedBy → checkPreCastConditions 每按一次都跑（含二段），
        //    不放行会导致二段被"未命中目标"拦截，onCast 不执行，爆裂永远触发不了。
        if (playerMagicData.getPlayerRecasts().hasRecastForSpell(this)) {
            return true;
        }
        // 首段：32 格视线锁定目标（吸准 0.35），失败提示由 helper 内部处理
        return Utils.preCastTargetHelper(level, entity, playerMagicData, this, 32, 0.35f);
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        // 二段瞬发：recast 激活时读条归零（门禁通过后下一 tick castSpell → queueBurst，
        // 不再重读 15t 条——连携爆裂手感即按下即蓄势）
        if (entity instanceof ServerPlayer serverPlayer
            && MagicData.getPlayerMagicData(serverPlayer).getPlayerRecasts().hasRecastForSpell(this)) {
            return 0;
        }
        return super.getEffectiveCastTime(spellLevel, entity);
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        // ⚠️ ISS 语义：getRecastCount = 总按压次数（首段 + recast），RecastInstance 构造器
        //    会做 remainingRecasts = totalRecasts - 1。传 1 会让 recast 瞬间失活——
        //    条被 isRecastActive 过滤不显示、二段被当成新施法。首段召唤 + 二段爆裂 = 2。
        return 2;
    }

    @Override
    public ICastDataSerializable getEmptyCastData() {
        return new SummonedEntitiesCastData();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        PlayerRecasts recasts = playerMagicData.getPlayerRecasts();

        if (!recasts.hasRecastForSpell(this)) {
            // ===== 首次施放：召唤血翼 =====
            Entity castTarget = null;
            if (playerMagicData.getAdditionalCastData() instanceof TargetEntityCastData castTargetingData) {
                castTarget = castTargetingData.getTarget((ServerLevel) level);
            }
            LivingEntity target = castTarget instanceof LivingEntity living && living != entity ? living : null;

            // 兜底：无 CastData 时自射线（AI 施法者等路径）
            if (target == null) {
                HitResult raycast = RaycastBuilder.begin(level, entity)
                    .range(32)
                    .checkForBlocks(true)
                    .build();
                if (raycast.getType() == HitResult.Type.ENTITY
                    && ((EntityHitResult) raycast).getEntity() instanceof LivingEntity living && living != entity) {
                    target = living;
                }
            }

            if (target != null && level instanceof ServerLevel serverLevel) {
                BloodwingEntity wing = new BloodwingEntity(serverLevel, entity, target, getSpellPower(spellLevel, entity));
                level.addFreshEntity(wing);
                SummonedEntitiesCastData data = new SummonedEntitiesCastData();
                SummonManager.initSummon(entity, wing, BloodwingCastCurve.LIFETIME_TICKS, data);
                RecastInstance recastInstance = new RecastInstance(
                    getSpellId(),
                    spellLevel,
                    getRecastCount(spellLevel, entity),
                    BloodwingCastCurve.LIFETIME_TICKS,
                    castSource,
                    data
                );
                recasts.addRecast(recastInstance, playerMagicData);
            }
        } else {
            // ===== Recast 二段：血翼蓄势爆裂（收尾消散） =====
            level.getEntitiesOfClass(BloodwingEntity.class, entity.getBoundingBox().inflate(64),
                    w -> entity.getUUID().equals(w.getOwnerUUID()))
                .forEach(BloodwingEntity::queueBurst);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}

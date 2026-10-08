package com.zzdzt.endfield_spellbook.spell.jingtingjue;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.element.SunderbladeEntity;
import com.zzdzt.endfield_spellbook.element.TargetMarkEntity;
import com.zzdzt.endfield_spellbook.element.ThunderStrikeHelper;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 青霆剑诀（庄方宜完整版战技「惊霆诀」的 MC 本地化，验证通过后接替旧惊霆诀）。
 *
 * <p>规则（拍板，对齐原作 Wiki）：
 * <ul>
 *   <li>产剑：有导电（≥1 档）→ 消耗导电并生成 min(3, 等级+1) 柄（本次雷击不吃导电易伤）；
 *       无导电 → 范围内剑 &lt;3 免费生成 1 柄，≥3 本回合不产剑（纯引导已有剑）</li>
 *   <li>施放时检测 {@link #GUIDANCE_RANGE} 范围内（以目标为中心）自己的剑进行引导，
 *       范围外的剑不纳入；满 9 剑的定义 = 释放时范围内剑数 ≥ 9</li>
 *   <li>剑 ≥9：不产剑、不消耗导电 → 导电异常保留，伤害走正常导电易伤（WP1 乘区 +12~24%）</li>
 *   <li>伤害解绑：首击落雷 ×1，每柄剑引导雷各 ×1，收尾最后一击 ×6（原作「最后一次雷击造成6倍伤害」），
 *       伤害在各自雷击帧结算（演出帧 = 命中帧）；首击/收尾为落点 {@link #AOE_RADIUS} 格群体伤害
 *       （收尾火帧再次定向目标当前位置），引导雷单体</li>
 *   <li>剑不消耗：36s 存场，反复施放累积（越战越勇）</li>
 *   <li>tier（红闪/雷瀑密度档）= min(4, 范围内剑数/2)</li>
 * </ul>
 */
public class SunderbladeStrikeSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "sunderblade_strike"
    );

    /** 数值占位：每道雷击的伤害基准（× 法术强度）。总量 = 首击1 + 每剑1 + 收尾6 ≈ (剑数+8)×基准×SP。 */
    public static final float STRIKE_BASE = 0.5f;
    /** 场上青霆剑上限（雷祖「九天雷炁」）。 */
    public static final int MAX_BLADES = 9;
    /** 首击落雷倍率。 */
    public static final float FIRST_STRIKE_MULT = 1.0f;
    /** 每柄剑引导雷倍率。 */
    public static final float PER_BLADE_MULT = 1.0f;
    /** 收尾最后一击倍率（原作：最后一次雷击造成6倍伤害）。 */
    public static final float FINALE_MULT = 6.0f;
    /** 首击/收尾的群体伤害半径（格）：以落点为中心。 */
    public static final float AOE_RADIUS = 1.0f;
    /** 引导范围（以目标为中心；范围外的剑不纳入引导与计数）。 */
    public static final float GUIDANCE_RANGE = 8.0f;
    // 雷击帧序列（首击延迟 / 引导基数与步进 / 收尾间隔 / GUIDANCE_SPEED 加速）收拢在 ThunderCastCurve——时间轴唯一真源。

    private static final int TARGET_RANGE = 32;
    private static final float TARGET_ANGLE = 0.35f;

    private final DefaultConfig config = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
        .setMaxLevel(3)
        .setCooldownSeconds(9)
        .build();

    public SunderbladeStrikeSpell() {
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 90;
        this.manaCostPerLevel = 0;
        this.castTime = 15;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        // 伤害模型：首击×1 + 每剑引导雷×1 + 收尾最后一击×6（均 STRIKE_BASE×SP）
        float strike = STRIKE_BASE * getSpellPower(spellLevel, caster);
        return List.of(
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".sunderblade_strike.info.strike",
                Utils.stringTruncation(strike, 1)),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".sunderblade_strike.info.finale",
                Utils.stringTruncation(strike * FINALE_MULT, 1)),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".sunderblade_strike.info.rule")
        );
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
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
        if (target == null || !target.isAlive()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        float spellPower = getSpellPower(spellLevel, entity);

        // 范围内自己的剑（目标为中心；范围外不纳入引导与计数）
        List<SunderbladeEntity> blades = guidedBlades(serverLevel, entity, target);
        int inRange = blades.size();

        // ===== 产剑规则（拍板）=====
        // 有导电（≥1 档）：消耗导电，生成 min(3, 档+1) 柄（本次雷击不吃导电易伤）
        // 无导电：范围内剑 <3 → 免费生成 1 柄；≥3 → 本回合不产剑（纯引导已有剑）
        // 范围内剑 ≥9：满剑态，不产剑、不消耗导电 → 导电易伤自动生效
        if (inRange < MAX_BLADES) {
            int stage = EndfieldElements.getElectrificationStage(target);
            int spawn;
            if (stage <= 0) {
                spawn = inRange < 3 ? 1 : 0; // 无导电：保底免费 1 柄 / 纯引导
            } else {
                spawn = Math.min(3, stage + 1); // 消耗导电 → min(3, 档+1) 柄
                target.removeEffect(EffectRegistry.ARTS_VULNERABLE.get()); // 本次雷击不吃易伤
            }
            spawn = Math.min(spawn, MAX_BLADES - inRange); // 上限裁剪
            if (spawn > 0) {
                spawnBlades(serverLevel, entity, target, inRange, spawn);
                // 重新收集（含新生剑，全部纳入引导）
                blades = guidedBlades(serverLevel, entity, target);
            }
        }

        // tier（雷瀑副瀑数量 + 红闪档）：按范围内剑数
        int tier = Math.min(4, blades.size() / 2);

        long now = serverLevel.getGameTime();

        // 目标脚下水墨印记（每次施放刷新；始终跟随主目标，最小 1.3 保证人形目标观感）
        serverLevel.addFreshEntity(new TargetMarkEntity(
            EntityRegistry.TARGET_MARK.get(), serverLevel,
            target, Math.max(1.3f, target.getBbWidth() * 1.0f), 40));

        // ① 首击：瀑布雷瀑 + 落点 1 格群体伤害
        ThunderStrikeHelper.scheduleFirstStrike(serverLevel, target.position(), tier, ThunderCastCurve.FIRST_STRIKE_DELAY);
        scheduleAoEStrikeDamage(serverLevel, entity, target.position(), STRIKE_BASE * spellPower * FIRST_STRIKE_MULT, ThunderCastCurve.FIRST_STRIKE_DELAY);

        // ② 每柄剑错峰：剑位雷 + 目标位雷 + 各自伤害（引导阶段整体按 GUIDANCE_SPEED 加速）
        double delay = ThunderCastCurve.GUIDANCE_BASE_DELAY / ThunderCastCurve.GUIDANCE_SPEED;
        boolean inkFlash = false;
        for (SunderbladeEntity blade : blades) {
            long at = Math.round(delay);
            blade.markStrikeAt(now + at); // 剑体闪白时点
            ThunderStrikeHelper.scheduleBladeStrike(serverLevel, blade.position(),
                target.position(), inkFlash, tier, at);
            scheduleStrikeDamage(serverLevel, entity, target, STRIKE_BASE * spellPower * PER_BLADE_MULT, at);
            inkFlash = !inkFlash;
            delay += ThunderCastCurve.GUIDANCE_STEP / ThunderCastCurve.GUIDANCE_SPEED;
        }

        // ③ 收尾最后一击 ×6（红芯雷瀑 + 群体伤害，火帧时再次定向目标当前位置）
        long finaleAt = Math.round(delay + ThunderCastCurve.FINALE_GAP / ThunderCastCurve.GUIDANCE_SPEED);
        ThunderStrikeHelper.scheduleFinale(serverLevel, target, target.position(), tier, finaleAt);
        scheduleAoEStrikeDamage(serverLevel, entity, target, target.position(), STRIKE_BASE * spellPower * FINALE_MULT, finaleAt);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 范围内自己的剑（以目标为中心 GUIDANCE_RANGE）。 */
    private static List<SunderbladeEntity> guidedBlades(ServerLevel level, LivingEntity caster, LivingEntity target) {
        return level.getEntitiesOfClass(SunderbladeEntity.class,
            target.getBoundingBox().inflate(GUIDANCE_RANGE),
            b -> caster.getUUID().equals(b.getOwnerId()) && !b.isRemoved());
    }

    /** 在目标脚下环形插剑。 */
    private static void spawnBlades(ServerLevel level, LivingEntity caster, LivingEntity target,
                                    int startIndex, int count) {
        float ringRadius = Math.max(3.0f, target.getBbWidth() * 0.75f + 2.0f);
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            double ang = Math.toRadians(index * 40f);
            Vec3 pos = target.position()
                .add(Math.cos(ang) * ringRadius, 0, Math.sin(ang) * ringRadius);
            level.addFreshEntity(new SunderbladeEntity(
                EntityRegistry.SUNDERBLADE.get(), level, pos,
                caster.getUUID(), target.getId(), index));
        }
    }

    /** 排入延迟队列：在雷击帧对引导目标结算单体伤害（演出帧 = 命中帧）。剑雷专用。 */
    private void scheduleStrikeDamage(ServerLevel level, LivingEntity caster, LivingEntity target,
                                      float amount, long delay) {
        ThunderStrikeHelper.schedule(level, delay, () -> {
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                DamageSources.applyDamage(target, amount, this.getDamageSource(caster));
            }
        });
    }

    /** 排入延迟队列：在雷击帧对落点周围 {@link #AOE_RADIUS} 格结算群体伤害（判定沿用旧水墨雷击实体模式）。 */
    private void scheduleAoEStrikeDamage(ServerLevel level, LivingEntity caster, Vec3 center,
                                         float amount, long delay) {
        ThunderStrikeHelper.schedule(level, delay, () -> applyAoEDamage(level, caster, center, amount));
    }

    /** 同上，但火帧时再次定向目标当前位置；目标已死/移除则退回 fallbackPos。收尾雷专用。 */
    private void scheduleAoEStrikeDamage(ServerLevel level, LivingEntity caster, LivingEntity target,
                                         Vec3 fallbackPos, float amount, long delay) {
        ThunderStrikeHelper.schedule(level, delay, () ->
            applyAoEDamage(level, caster, target.isAlive() ? target.position() : fallbackPos, amount));
    }

    /** 以 center 为中心 {@link #AOE_RADIUS} 格群体伤害：排除施法者/友方/不可选目标，逐个清无敌帧后结算。 */
    private void applyAoEDamage(ServerLevel level, LivingEntity caster, Vec3 center, float amount) {
        var damageSource = this.getDamageSource(caster);
        AABB damageBox = new AABB(
            center.x - AOE_RADIUS, center.y - 0.5, center.z - AOE_RADIUS,
            center.x + AOE_RADIUS, center.y + 2.5, center.z + AOE_RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, damageBox,
            e -> e != caster &&
                 e.isAlive() &&
                 e.isPickable() &&
                 !Utils.shouldHealEntity(caster, e) &&
                 e.distanceToSqr(center.x, center.y, center.z) <= AOE_RADIUS * AOE_RADIUS);
        for (LivingEntity victim : victims) {
            // 多段连击（首击/引导雷/收尾接踵而至）：先清无敌帧，防本段被 i-frame 吞伤
            victim.invulnerableTime = 0;
            DamageSources.applyDamage(victim, amount, damageSource);
        }
    }

    @Nullable
    private LivingEntity getTargetFromCastData(MagicData magicData, Level level) {
        var castData = magicData.getAdditionalCastData();
        if (!(castData instanceof TargetEntityCastData targetData)) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
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

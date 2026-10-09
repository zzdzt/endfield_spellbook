package com.zzdzt.endfield_spellbook.spell.smoulderingfire;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.element.PlayerChargeType;
import com.zzdzt.endfield_spellbook.element.PlayerCharges;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.FlameStrikeParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 焚灭（莱万汀化）
 *
 * 资源循环（终末地莱万汀「灼心」的 MC 本地化）：
 * - 施放时吸收横扫范围内敌人身上的灼热附着 → 每层转化为 1 层熔火（上限 4，不衰减，死亡清空）
 * - 满 4 层熔火时施放 → 强化焚灭：范围扩大 ×1.5、伤害 ×2、火焰爆炸演出，结算后清空熔火
 * - 焚灭本身不挂灼热附着（生产者角色由未来体系承担）
 */
public class SmoulderingFireSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "smouldering_fire"
    );

    private static final float RADIUS = 5.5f;
    private static final float DISTANCE = 3.5f;

    // 强化焚灭倍率（常量可调）
    private static final float ENHANCED_RADIUS_MULT = 1.5f;
    private static final float ENHANCED_DAMAGE_MULT = 2.0f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(18)
            .build();

    public SmoulderingFireSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 4;
        this.castTime = 15;
        this.baseManaCost = 60;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("ui.irons_spellbooks.damage", getDamageText(spellLevel, caster)),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".smouldering_fire.info.enhanced")
        );
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.FLAMING_STRIKE_UPSWING.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.FLAMING_STRIKE_SWING.get());
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
    public boolean canBeInterrupted(@Nullable Player player) {
        return false;
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        return getCastTime(spellLevel);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {

        Vec3 forward = entity.getForward();
        Vec3 hitLocation = entity.position()
            .add(0, entity.getBbHeight() * 0.3f, 0)
            .add(forward.scale(DISTANCE));

        // ① 吸收：横扫范围内的敌人身上的灼热附着 → 熔火
        List<LivingEntity> targets = collectTargets(level, entity, hitLocation, RADIUS);
        boolean slashAttack = hasSlashWeapon(entity);
        float radius = RADIUS;
        float damageMult = 1.0f;

        if (level instanceof ServerLevel serverLevel) {
            int absorbed = 0;
            for (LivingEntity target : targets) {
                absorbed += EndfieldElements.consume(target, EndfieldElement.HEAT);
            }
            PlayerCharges.gain(entity, PlayerChargeType.MOLTEN_FIRE, absorbed);

            // ② 判定：满 4 层熔火 → 强化焚灭（范围 ×1.5、伤害 ×2、火焰爆炸），并清空熔火
            if (PlayerCharges.peek(entity, PlayerChargeType.MOLTEN_FIRE)
                >= PlayerChargeType.MOLTEN_FIRE.getMaxStacks()) {
                PlayerCharges.consumeAll(entity, PlayerChargeType.MOLTEN_FIRE);
                radius *= ENHANCED_RADIUS_MULT;
                damageMult = ENHANCED_DAMAGE_MULT;
                launchEnhanced(serverLevel, entity, hitLocation, radius, forward);
                targets = collectTargets(level, entity, hitLocation, radius);
            }

            // 幻影魔剑协同（纯视觉，无伤害）：主手武器有攻击伤害时投影横扫（与斩击同向）
            if (slashAttack) {
                boolean mirrored = playerMagicData.getCastingEquipmentSlot()
                    .equals(SpellSelectionManager.OFFHAND);
                // 焚灭大回环斩：幻影魔剑砍出火环（环头=剑尖，弧面跟随施法准星），火焰顺剑势跑满一圈后碎裂
                float[] angles = slashAngles(forward, mirrored);
                spawnPhantomBlade(serverLevel, entity, chantOffset(entity, forward), angles[0], angles[1],
                    entity.getXRot(), entity.getYRot());
                level.addFreshEntity(new FlameRingEntity(
                    EntityRegistry.FLAME_RING.get(), serverLevel,
                    entity.position(), DISTANCE, angles[0], angles[1],
                    entity.getXRot(), entity.getYRot()));
                // Share the visual ring's origin, radius, plane and reveal curve for authoritative hits.
                level.addFreshEntity(new FlameRingAttackEntity(
                    EntityRegistry.FLAME_RING_ATTACK.get(), serverLevel, entity,
                    this.getDamageSource(entity), getDamage(spellLevel, entity) * damageMult,
                    entity.position(), DISTANCE, angles[0], angles[1],
                    entity.getXRot(), entity.getYRot()));
            }
        }

        // ③ 魔剑/火环演出使用新弧段逐 tick 命中；无魔剑演出时保留旧范围伤害作为兼容回退。
        if (!slashAttack) {
            var damageSource = this.getDamageSource(entity);
            for (LivingEntity livingTarget : targets) {
                float baseDamage = getDamage(spellLevel, entity) * damageMult;
                if (entity.distanceToSqr(livingTarget) >= radius * radius) continue;

                if (DamageSources.applyDamage(livingTarget, baseDamage, damageSource)) {
                    MagicManager.spawnParticles(level, ParticleHelper.FIRE,
                        livingTarget.getX(),
                        livingTarget.getY() + livingTarget.getBbHeight() * 0.5f,
                        livingTarget.getZ(),
                        30,
                        livingTarget.getBbWidth() * 0.5f,
                        livingTarget.getBbHeight() * 0.5f,
                        livingTarget.getBbWidth() * 0.5f,
                        0.03, false);

                    EnchantmentHelper.doPostDamageEffects(entity, livingTarget);
                }
            }
        }

        boolean mirrored = playerMagicData.getCastingEquipmentSlot()
            .equals(SpellSelectionManager.OFFHAND);

        MagicManager.spawnParticles(level,
            new FlameStrikeParticleOptions(
                (float) forward.x,
                (float) forward.y,
                (float) forward.z,
                mirrored,
                false,
                1.2f
            ),
            hitLocation.x,
            hitLocation.y + 0.5,
            hitLocation.z,
            1, 0, 0, 0, 0, true
        );

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 强化焚灭的参数与演出（服务端调用）。 */
    private void launchEnhanced(ServerLevel level, LivingEntity caster,
                                Vec3 hitLocation, float radius, Vec3 forward) {
        // 橙红迸发：Blastwave 环 + Spark 火花（元素迸发模板，灼热色）
        float[] c = EndfieldElement.HEAT.particleColor();
        Vector3f heat = new Vector3f(c[0], c[1], c[2]);
        MagicManager.spawnParticles(level,
            new BlastwaveParticleOptions(heat, 1.5f),
            hitLocation.x, hitLocation.y, hitLocation.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level,
            new SparkParticleOptions(heat),
            hitLocation.x, hitLocation.y, hitLocation.z, 35, 0.25f, 0.25f, 0.25f, 0.3f, true);
        level.playSound(null, hitLocation.x, hitLocation.y, hitLocation.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.1f);
    }

    /**
     * 挥砍弧角：主手右翼起扫 80°（±40°，对齐 ISS FlameStrike 特效区域），副手镜像反向。
     * 返回 {起始角, 扫过角, 视线水平角}——起始/扫过角为水平面约定，
     * 视线俯仰由调用方另行传入（弧面跟随准星，ISS 同款）。
     */
    private static float[] slashAngles(Vec3 forward, boolean mirrored) {
        float centerAngle = (float) Math.toDegrees(Math.atan2(forward.z, forward.x)); // forward 方向角
        float startAngle = centerAngle + (mirrored ? 40f : -40f);
        float sweepAngle = mirrored ? -80f : 80f;
        return new float[]{startAngle, sweepAngle, centerAngle};
    }

    /**
     * 主手是否持有具备攻击伤害的武器——幻影魔剑系演出的统一开关
     * （吟唱投影 / 幻影魔剑 / 焚灭火环共用；无伤害物品如火把、木棍不触发整套演出）。
     */
    public static boolean hasSlashWeapon(LivingEntity entity) {
        return Utils.getWeaponDamage(entity, MobType.UNDEFINED) > 0f;
    }

    /** 背部吟唱浮位（相对施放者，后移两格），幻影魔剑与焚灭火环的衔接段共用。 */
    private static Vec3 chantOffset(LivingEntity caster, Vec3 forward) {
        Vec3 back = forward.scale(-1);
        Vec3 right = new Vec3(forward.z, 0, -forward.x);
        return back.scale(2.4)
            .add(right.scale(0.15))
            .add(0, caster.getBbHeight() * 0.55, 0);
    }

    /** 幻影魔剑横扫（纯视觉）：投影主手武器沿准星斜面弧线扫过（弧参数见 {@link #slashAngles}）。 */
    private static void spawnPhantomBlade(ServerLevel level, LivingEntity caster, Vec3 chantOffset,
                                          float startAngle, float sweepAngle,
                                          float lookPitch, float lookYaw) {
        ItemStack weapon = caster.getMainHandItem();
        if (weapon.isEmpty()) return;

        level.addFreshEntity(new PhantomBladeEntity(
            EntityRegistry.PHANTOM_BLADE.get(), level, weapon.copy(),
            caster.position(), chantOffset, DISTANCE, startAngle, sweepAngle, false,
            lookPitch, lookYaw));
        // 命中点余烬爆散（挥砍终点的火星反馈，纯视觉）
        var hitPos = caster.getEyePosition().add(caster.getForward().scale(DISTANCE));
        MagicManager.spawnParticles(level, ParticleHelper.EMBERS,
            hitPos.x, hitPos.y, hitPos.z, 18, 0.25, 0.25, 0.25, 0.02, true);
    }

    /** 强化态的横扫半径。 */
    private static float radiusFor(float base) {
        return base * ENHANCED_RADIUS_MULT;
    }

    /**
     * 收集横扫范围内的有效目标：包围盒粗筛 → 前向/距离/视线精筛。
     */
    private static List<LivingEntity> collectTargets(Level level, LivingEntity caster,
                                                     Vec3 hitLocation, float radius) {
        List<LivingEntity> targets = new ArrayList<>();
        Vec3 forward = caster.getForward();
        for (Entity targetEntity : level.getEntities(caster,
            AABB.ofSize(hitLocation, radius * 2, radius, radius * 2))) {
            if (!(targetEntity instanceof LivingEntity livingTarget)) continue;
            if (!livingTarget.isAlive()) continue;
            if (!livingTarget.isPickable()) continue;

            Vec3 toTarget = targetEntity.position().subtract(caster.getEyePosition());
            if (toTarget.dot(forward) < 0) continue;
            if (caster.distanceToSqr(targetEntity) >= radius * radius) continue;
            if (!Utils.hasLineOfSight(level, caster.getEyePosition(),
                targetEntity.getBoundingBox().getCenter(), true)) continue;

            targets.add(livingTarget);
        }
        return targets;
    }

    /**
     * 计算对目标的伤害。
     * 包含：法术强度 + 主手武器伤害 + 副手武器伤害 + 双手火焰附加等级。
     */
    private float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster)
           + getTotalWeaponDamage(caster)           // 主手+副手武器伤害
           + getTotalFireAspect(caster);            // 主手+副手火焰附加
    }

    // 计算主手 + 副手武器伤害总和
    private float getTotalWeaponDamage(LivingEntity entity) {
        float total = Utils.getWeaponDamage(entity, MobType.UNDEFINED); // 主手

        // 副手武器伤害
        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        total += getItemAttackDamage(offhand);

        return total;
    }

    // 从物品属性读取攻击伤害
    private float getItemAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        double damage = 0;
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        // 注意：AttributeModifier 的 amount 已经包含了基础值和加成
        // 实际伤害 = amount（基础攻击 + 物品伤害 - 1，因为玩家基础攻击1已计入）
        return (float) damage;
    }

    // 计算主手 + 副手火焰附加等级总和
    private int getTotalFireAspect(LivingEntity entity) {
        int total = EnchantmentHelper.getFireAspect(entity); // 主手

        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        total += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, offhand);

        return total;
    }

    //提示信息

    private String getDamageText(int spellLevel, LivingEntity entity) {
        if (entity != null) {
            float weaponDamage = getTotalWeaponDamage(entity);
            String plus = "";
            if (weaponDamage > 0) {
                plus = String.format(" (+%s)", Utils.stringTruncation(weaponDamage, 1));
            }
            String damage = Utils.stringTruncation(
                getSpellPower(spellLevel, entity) + weaponDamage, 1);
            return damage + plus;
        }
        return "" + getSpellPower(spellLevel, entity);
    }

    //动画

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }
}

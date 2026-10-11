package com.zzdzt.endfield_spellbook.spell.windarrow;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.element.PlayerChargeType;
import com.zzdzt.endfield_spellbook.element.PlayerCharges;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 风矢穿林：首次施法启动起飞；之后每次 Recast 执行一轮箭矢攻击。猎矢用于强化，不限制普通攻击。
 *
 * <p>每轮先进行玩家准星主箭，再向前方半平面内选择不同的合法敌人发射智能副箭。
 * 命中自然附着目标时，消耗 1 层附着并按消耗前层数结算提弗洛斯式强化爆发。
 * 同一轮无论两支箭触发几次强化爆发，最多返还 1 支猎矢并返还本轮已消耗的 Recast 次数。
 */
public class WindArrowSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
        ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "wind_arrow");

    private static final float MAIN_RANGE = 32.0f;
    private static final double SECONDARY_RANGE = 16.0;
    private static final int MAX_RECAST_COUNT = 6;
    private static final int RECAST_LIFETIME_TICKS = 20 * 5;
    private static final float DIRECT_DAMAGE_COEFFICIENT = 1.0f;
    private static final float NATURE_EMPOWER_COEFFICIENT = 1.6f;
    private static final float TRAIL_STEP = 0.65f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
        .setMaxLevel(10)
        .setCooldownSeconds(8)
        .build();

    public WindArrowSpell() {
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 18;
        this.manaCostPerLevel = 3;
        this.castTime = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
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
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        // 总 Recast 槽位固定为六次：启动浮空 1 次 + 保底空中攻击 1 次 + 最多 4 支猎矢。
        return MAX_RECAST_COUNT;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        return List.of(
            Component.translatable("ui.irons_spellbooks.damage",
                Utils.stringTruncation(power * DIRECT_DAMAGE_COEFFICIENT, 1)),
            Component.translatable("spell." + EndfieldSpellbook.MOD_ID + ".wind_arrow.info.empower",
                Utils.stringTruncation(EndfieldElements.BURST_BASE * power * NATURE_EMPOWER_COEFFICIENT, 1))
        );
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.ARROW_SHOOT);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        // Recast 表示当前仍处于风矢穿林空中攻击阶段。猎矢只决定是否强化，不是普通攻击的门槛。
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!(level instanceof ServerLevel serverLevel) || !caster.isAlive()) {
            super.onCast(level, spellLevel, caster, castSource, magicData);
            return;
        }

        PlayerRecasts recasts = magicData.getPlayerRecasts();
        boolean recastCast = recasts.hasRecastForSpell(this);

        if (!recastCast) {
            // 猎矢是玩家资源：首次由玩家使用时才初始化为 4 层；非玩家施法者维持原有零库存行为。
            int stockSnapshot = 0;
            if (caster instanceof ServerPlayer) {
                PlayerCharges.initialize(caster, PlayerChargeType.HUNTING_ARROWS);
                stockSnapshot = Math.min(
                    PlayerCharges.peek(caster, PlayerChargeType.HUNTING_ARROWS),
                    PlayerChargeType.HUNTING_ARROWS.getMaxStacks()
                );
            }

            // 首次按压只负责启动风矢穿林：起飞并进入滞空，不发射箭矢。
            // 快照最多 4 支猎矢；启动后空中攻击次数 = 保底 1 次 + 猎矢快照。
            // RecastInstance 的 total 固定为 6（启动 1 次 + 最多 5 次空中攻击），
            // 再将 remainingRecasts 设为 1 + stockSnapshot，让零猎矢时也保留 1 次攻击。
            int availableAttacks = 1 + stockSnapshot;
            RecastInstance instance = new RecastInstance(
                getSpellId(), spellLevel, MAX_RECAST_COUNT, RECAST_LIFETIME_TICKS, castSource, null
            );
            var tag = instance.serializeNBT();
            tag.putInt("remainingRecasts", availableAttacks);
            instance.deserializeNBT(tag);
            recasts.addRecast(instance, magicData);
        } else {
            // 后续每次 Recast 才是空中箭矢攻击。没有猎矢时照常射击；有猎矢时先消耗一支，
            // 让本轮箭矢可以按原有逻辑触发强化与资源奖励。
            PlayerCharges.consume(caster, PlayerChargeType.HUNTING_ARROWS, 1);

            boolean empoweredThisRound = fireRound(serverLevel, caster, spellLevel);
            if (rewardArrowIfPossible(caster, empoweredThisRound)) {
                // 暂时返还 1 次 remainingRecasts；ISS 随后自动递减时抵消本轮消耗。
                // 不修改 totalRecasts，因此不会增加 Recast 上限。
                refundActiveRecast(recasts);
            }
        }

        // 首次施法启动起飞；后续每次攻击 Recast 都刷新 30 秒滑动期限。
        // 起飞状态绑定真实 Recast 生命周期；次数耗尽或超时后由飞行控制器恢复玩家状态。
        if (caster instanceof ServerPlayer serverPlayer) {
            boolean tiedToRecast = recastCast || recasts.hasRecastForSpell(getSpellId());
            WindArrowFlightController.startOrRefresh(
                serverPlayer, tiedToRecast, RECAST_LIFETIME_TICKS
            );
        }

        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    /**
     * 每轮只结算一次奖励：任一箭触发强化自然爆发即可；两箭都触发也不叠加奖励。
     * 返回 true 表示确实回充了猎矢，因此也有资格返还 Recast。
     */
    private static boolean rewardArrowIfPossible(LivingEntity caster, boolean empowered) {
        if (!(caster instanceof ServerPlayer) || !empowered) return false;
        if (PlayerCharges.peek(caster, PlayerChargeType.HUNTING_ARROWS)
            >= PlayerChargeType.HUNTING_ARROWS.getMaxStacks()) {
            return false;
        }
        PlayerCharges.gain(caster, PlayerChargeType.HUNTING_ARROWS, 1);
        return true;
    }

    /**
     * 返还本轮消耗的 Recast 使用次数。ISS 在 onCast 返回后会自动减 1，
     * 因此只增加 remainingRecasts，不增加 totalRecasts；最终可用次数保持不变。
     */
    private void refundActiveRecast(PlayerRecasts recasts) {
        RecastInstance instance = recasts.getRecastInstance(getSpellId());
        if (instance == null) return;

        var tag = instance.serializeNBT();
        tag.putInt("remainingRecasts", tag.getInt("remainingRecasts") + 1);
        instance.deserializeNBT(tag);
        // AbstractSpell.castSpell 随后会调用 decrementRecastCount，并同步最终状态。
    }

    /**
     * 主箭严格按准星射线；副箭另行智能选敌。每轮最多两支箭，服务器负责命中与伤害。
     */
    private boolean fireRound(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 eye = caster.getEyePosition();
        HitResult mainHit = Utils.raycastForEntity(level, caster, MAIN_RANGE, true);
        Vec3 mainEnd = mainHit.getLocation();
        LivingEntity mainTarget = null;

        if (mainHit instanceof EntityHitResult entityHit
            && entityHit.getEntity() instanceof LivingEntity living
            && isLegalTarget(caster, living)) {
            mainTarget = living;
        }
        spawnArrowTrail(level, eye, mainEnd);
        level.playSound(null, caster.blockPosition(), SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS, 0.55f, 1.35f);

        float spellPower = getSpellPower(spellLevel, caster);
        boolean empowered = false;
        if (mainTarget != null) {
            empowered |= hitWithArrow(level, caster, mainTarget, spellPower);
        }

        LivingEntity secondary = findSecondaryTarget(level, caster, mainTarget);
        if (secondary != null) {
            Vec3 secondaryEnd = secondary.getBoundingBox().getCenter();
            spawnArrowTrail(level, eye, secondaryEnd);
            level.playSound(null, caster.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.42f, 1.65f);
            empowered |= hitWithArrow(level, caster, secondary, spellPower);
        }
        return empowered;
    }

    private static boolean isLegalTarget(LivingEntity caster, LivingEntity target) {
        return target != caster
            && target.isAlive()
            && !target.isRemoved()
            && !target.isSpectator()
            && !target.isInvulnerable()
            && !caster.isAlliedTo(target)
            && (!(target instanceof Player player) || !player.isCreative() && !player.isSpectator());
    }

    /**
     * 副箭范围以施法者为中心；水平前半平面 dot >= 0（左右各 90 度）。
     * 自然附着目标优先，再按朝向和距离打分；方块遮挡目标不入选。
     */
    @Nullable
    private static LivingEntity findSecondaryTarget(ServerLevel level, LivingEntity caster,
                                                     @Nullable LivingEntity mainTarget) {
        Vec3 look = caster.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        if (forward.lengthSqr() < 1.0e-6) {
            forward = new Vec3(0, 0, 1);
        } else {
            forward = forward.normalize();
        }

        final Vec3 horizontalForward = forward;
        return level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(SECONDARY_RANGE),
                target -> isLegalTarget(caster, target)
                    && target != mainTarget
                    && target.distanceToSqr(caster) <= SECONDARY_RANGE * SECONDARY_RANGE)
            .stream()
            .filter(target -> {
                Vec3 delta = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
                Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
                if (horizontal.lengthSqr() < 1.0e-6) return true;
                return horizontalForward.dot(horizontal.normalize()) >= -1.0e-4;
            })
            .filter(target -> hasLineOfSight(level, caster, target))
            .max(Comparator
                .comparingInt((LivingEntity target) ->
                    EndfieldElements.hasElement(target, EndfieldElement.NATURE) ? 1 : 0)
                .thenComparingDouble(target -> {
                    Vec3 delta = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
                    Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
                    double dot = horizontal.lengthSqr() < 1.0e-6
                        ? 1.0 : horizontalForward.dot(horizontal.normalize());
                    double distance = Math.sqrt(target.distanceToSqr(caster));
                    return dot * 100.0 - distance;
                }))
            .orElse(null);
    }

    private static boolean hasLineOfSight(ServerLevel level, LivingEntity caster, LivingEntity target) {
        HitResult blockHit = level.clip(new ClipContext(
            caster.getEyePosition(),
            target.getBoundingBox().getCenter(),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            caster
        ));
        return blockHit.getType() == HitResult.Type.MISS;
    }

    /**
     * 强化爆发先于直击结算，以保证有效命中目标即使被直击击杀也不会跳过附着消费。
     * consumeOne 返回消耗前层数；伤害 = ISS 法强 × 当前自然层数 × 6 × 1.6。
     */
    private boolean hitWithArrow(ServerLevel level, LivingEntity caster, LivingEntity target, float spellPower) {
        boolean empowered = false;
        if (EndfieldElements.hasElement(target, EndfieldElement.NATURE)) {
            int preConsumeStacks = EndfieldElements.consumeOne(target, EndfieldElement.NATURE);
            if (preConsumeStacks > 0) {
                float burstDamage = EndfieldElements.BURST_BASE * spellPower
                    * preConsumeStacks * NATURE_EMPOWER_COEFFICIENT;
                hurtWithNatureMagic(level, caster, target, burstDamage);
                spawnNatureBurst(level, target);
                empowered = true;
            }
        }

        hurtWithNatureMagic(level, caster, target, spellPower * DIRECT_DAMAGE_COEFFICIENT);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            8, 0.18, 0.25, 0.18, 0.08);
        return empowered;
    }

    private static void hurtWithNatureMagic(ServerLevel level, LivingEntity caster,
                                            LivingEntity target, float amount) {
        if (amount <= 0 || target.isRemoved()) return;
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(ISSDamageTypes.NATURE_MAGIC);
        DamageSource source = new DamageSource(holder, null, caster);
        target.invulnerableTime = 0;
        target.hurt(source, amount);
    }

    private static void spawnNatureBurst(ServerLevel level, LivingEntity target) {
        float[] rgb = EndfieldElement.NATURE.particleColor();
        Vector3f color = new Vector3f(rgb[0], rgb[1], rgb[2]);
        Vec3 pos = target.position().add(0, 0.1, 0);
        MagicManager.spawnParticles(level, new BlastwaveParticleOptions(color, 1.15f),
            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level, new SparkParticleOptions(color),
            pos.x, pos.y + target.getBbHeight() * 0.5, pos.z, 18,
            0.2f, 0.25f, 0.2f, 0.2f, true);
    }

    private static void spawnArrowTrail(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / TRAIL_STEP));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = start.add(delta.scale((double) i / steps));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                point.x, point.y, point.z, 1, 0.015, 0.015, 0.015, 0.005);
        }
    }
}

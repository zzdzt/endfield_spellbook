package com.zzdzt.endfield_spellbook.spell.tidalwave;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.spell.tidalwave.entity.WhirlpoolEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.player.SpinAttackType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 踏潮卷浪！（终末地·汤汤战技）：水元素位移型资源循环战技。
 *
 * <p>技能循环（拍板）：
 * <pre>
 * Cast → 读取并消耗自身涡流（记录位置+数量入 persistentData）
 *      → VoltStrike 式潮汐突进（8t 冲刺窗）
 *      → 冲刺结束（撞击提前 or 到期）触发寒冷冲击
 *      → 按消耗记录在原涡流位置生成水龙卷（持续寒冷输出）
 *      → 按命中敌人数（≤2）在敌人位置生成新涡流
 * </pre>
 *
 * <p>职责边界（架构规范 §4）：只做施法条件/消耗/资源读取/冲刺发起；
 * 冲击结算、实体生成在 {@link TidalWaveDashEffect}；渲染/粒子不在本类。
 */
public class TidalWaveSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        EndfieldSpellbook.MOD_ID, "tidal_wave");

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
        .setMaxLevel(5)
        .setCooldownSeconds(10)
        .build();

    public TidalWaveSpell() {
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
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
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage",
            getImpactDamage(spellLevel, caster)));
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.TRIDENT_RIPTIDE_3);
    }

    /** 潮汐冲击伤害（占位待调）：4 + 法强。 */
    private int getImpactDamage(int spellLevel, LivingEntity caster) {
        return (int) (4 + getSpellPower(spellLevel, caster));
    }

    /** 水龙卷每跳伤害（占位待调）：1 + 0.35×法强。 */
    private float getTornadoTickDamage(int spellLevel, LivingEntity caster) {
        return 1 + 0.35f * getSpellPower(spellLevel, caster);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        CompoundTag data = entity.getPersistentData();

        // ① 读取并消耗自身涡流：位置+数量+预算伤害写入 pending，随后销毁
        List<WhirlpoolEntity> whirlpools = level.getEntitiesOfClass(WhirlpoolEntity.class,
            entity.getBoundingBox().inflate(TidalWaveCastCurve.WHIRLPOOL_CONSUME_RANGE),
            w -> !w.isRemoved() && entity.getUUID().equals(w.getOwnerId()));
        CompoundTag pending = new CompoundTag();
        ListTag positions = new ListTag();
        // 钳制：pending 最多记录 WHIRLPOOL_MAX 个（离施法者最近的优先），
        // 多余涡流仅消耗不转化——"1 涡流 = 1 水龙卷、最多 2 龙卷"永不突破
        whirlpools.sort(Comparator.comparingDouble(w -> w.distanceToSqr(entity)));
        int recorded = Math.min(whirlpools.size(), TidalWaveCastCurve.WHIRLPOOL_MAX);
        for (int i = 0; i < recorded; i++) {
            WhirlpoolEntity whirlpool = whirlpools.get(i);
            CompoundTag p = new CompoundTag();
            p.putDouble("x", whirlpool.getX());
            p.putDouble("y", whirlpool.getY());
            p.putDouble("z", whirlpool.getZ());
            positions.add(p);
        }
        pending.putInt("cnt", recorded);
        pending.putFloat("dmg", getTornadoTickDamage(spellLevel, entity));
        pending.putLong("t", level.getGameTime());
        pending.put("pos", positions);
        data.put(TidalWaveDashEffect.PENDING_KEY, pending);
        whirlpools.forEach(WhirlpoolEntity::discard);

        // ② 开新冲刺窗：清上次触发标志
        data.remove(TidalWaveDashEffect.STRUCK_KEY);

        // ③ 潮汐突进（VoltStrike 公式）
        entity.hasImpulse = true;
        float multiplier = (15 + getSpellPower(spellLevel, entity)) / 20f;
        Vec3 forward = entity.getLookAngle();
        var upwardness = forward.dot(new Vec3(0, 1, 0));
        var remap = 1 - (Math.max(0, upwardness) * 0.6f);
        var impulse = forward.scale(3 * multiplier).multiply(1, remap, 1);
        if (entity.onGround()) {
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.teleport(serverPlayer.getX(), serverPlayer.getY() + 1,
                    serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
            } else {
                entity.move(MoverType.SELF, new Vec3(0.0, 1.1999999F, 0.0));
            }
            impulse.add(0, 0.5, 0);
        } else {
            impulse.add(0, 0.25, 0);
        }
        entity.setDeltaMovement(new Vec3(
            Mth.lerp(.75f, entity.getDeltaMovement().x, impulse.x),
            Mth.lerp(.75f, entity.getDeltaMovement().y, impulse.y),
            Mth.lerp(.75f, entity.getDeltaMovement().z, impulse.z)
        ));
        entity.hurtMarked = true;
        entity.invulnerableTime = 20;
        playerMagicData.getSyncedData().setSpinAttackType(SpinAttackType.RIPTIDE);

        // ④ 挂冲刺窗（amplifier = 预算冲击伤害）
        entity.addEffect(new MobEffectInstance(EffectRegistry.TIDAL_WAVE_DASH.get(),
            TidalWaveCastCurve.DASH_WINDOW_TICKS, getImpactDamage(spellLevel, entity),
            false, false, false));

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}

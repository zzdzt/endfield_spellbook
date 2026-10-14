package com.zzdzt.endfield_spellbook.spell.tidalwave.entity;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.entity.CasterOwnedEntity;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import com.zzdzt.endfield_spellbook.spell.tidalwave.TidalWaveCastCurve;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 水龙卷（踏潮卷浪）：消耗涡流在其原位置生成的持续寒冷输出实体。
 *
 * <p>继承 ISS 官方持续范围基类 {@link AoeEntity}（duration/reapplicationDelay/radius/
 * 周期 checkHits 现成，BlizzardAoe 同款先例）。伤害视为 Spell Damage（法术伤害源，
 * 非召唤物伤害）。
 *
 * <p>寒冷附着规则（拍板）：每个水龙卷对同一目标只附着一次（{@link #affectedTargets}
 * 记录）；多个水龙卷独立，单次释放最多 +2 层。
 *
 * <p>不负责：创建涡流 / 技能释放逻辑 / 玩家状态。
 */
public class WaterTornadoEntity extends AoeEntity implements CasterOwnedEntity {

    /** 本龙卷已挂过寒冷附着的目标（每目标仅一次）。 */
    private final Set<UUID> affectedTargets = new HashSet<>();

    public WaterTornadoEntity(EntityType<? extends AoeEntity> type, Level level) {
        super(type, level);
    }

    public WaterTornadoEntity(EntityType<? extends AoeEntity> type, Level level, Vec3 pos,
                              LivingEntity owner, float damagePerTick) {
        this(type, level);
        this.setPos(pos);
        this.setOwner(owner);
        this.setDamage(damagePerTick);
        this.setRadius(TidalWaveCastCurve.TORNADO_RADIUS);
        this.setCircular();
        this.setDuration(TidalWaveCastCurve.TORNADO_LIFETIME_TICKS);
        this.setReapplicationDelay(TidalWaveCastCurve.TORNADO_TICK_INTERVAL);
    }

    /** 每跳：寒冷伤害 + 首次命中挂 1 层寒冷附着。 */
    @Override
    public void applyEffect(LivingEntity target) {
        LivingEntity owner = getDirectOwner();
        if (owner == null) {
            return;
        }
        DamageSources.applyDamage(target, getDamage(),
            SpellRegistry.TIDAL_WAVE.get().getDamageSource(owner));
        target.invulnerableTime = 0; // 周期伤害不被无敌帧挡住（项目惯例）
        if (affectedTargets.add(target.getUUID())) {
            EndfieldElements.apply(owner, target, EndfieldElement.CRYO);
        }
    }

    /** 拉高命中盒：龙卷视觉 3 格高，基类默认 1.2 高命中盒纵向偏窄。 */
    @Override
    protected Vec3 getInflation() {
        return new Vec3(0, 1.0, 0);
    }

    /** 地面环境粒子已关闭（基类 ambientParticles 以 empty Optional 短路），视觉走纯 ribbon 渲染。 */
    @Override
    public float getParticleCount() {
        return 0f;
    }

    @Override
    public Optional<ParticleOptions> getParticle() {
        return Optional.empty();
    }

    @Nullable
    @Override
    public LivingEntity getDirectOwner() {
        return getOwner() instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

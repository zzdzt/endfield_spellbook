package com.zzdzt.endfield_spellbook.spell.gloompurge;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import com.zzdzt.endfield_spellbook.registry.SpellRegistry;
import com.zzdzt.endfield_spellbook.spell.lizhiyan.LizhiYanBlastVisualEntity;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 破晦阵触发处理器（服务端权威）。
 *
 * 领域状态机：
 *   - 施法时 openDomain 记录（中心 = 施法点，固定不跟随），
 *     并生成两个印记实体：圆心八卦阵 + 边缘电波环
 *     （重复施法丢弃旧印记，场上始终只有一套领域视觉）
 *   - 到期自动失效（tick 检测懒清理）
 *   - 玩家登出清理
 *
 * 触发链（LivingHurtEvent）：
 *   - 攻击者持有活跃领域 → 目标在领域内 → 每目标短冷却通过
 *   → 进入蓄力队列（CHARGE_TICKS，起点蓝白三角收缩汇聚 + 下落拖尾）
 *   - 发射时自目标斜上方随机方位（与地面 60°）降下 lizhi_yan 同款 blast
 *     光束，命中带环形波纹与火花飞溅，音效为 starfall 彗星命中音，
 *     即时单体结算
 *   - 防递归：回声自身的 SpellDamageSource 不再触发
 *   - 除回声外，攻击者造成的任意伤害（近战/弹射物/其他法术）均触发
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class GloompurgeEventHandler {

    private GloompurgeEventHandler() {}

    // 视觉常量：青白色（lizhi_yan blast 同款色调）
    private static final float[] BLAST_TINT = {0.22f, 0.71f, 0.97f};

    // 天降打击几何：与地面 60°，斜上方随机方位
    private static final double STRIKE_ELEVATION = Math.toRadians(60);
    private static final double STRIKE_DISTANCE = 8.0;

    // 蓄力时长：触发后先蓄力演出，再发射光束
    private static final int CHARGE_TICKS = 6;

    // 蓄力三角起始半径（渲染器逐帧收缩到 0）
    private static final float CHARGE_TRI_RADIUS = 2.0f;

    // 八卦阵半径 = 领域半径 × 该比例（边缘装饰环另用完整领域半径）
    private static final float SIGIL_RADIUS_RATIO = 0.15f;

    // 领域状态

    private static final class Domain {
        final Level level;
        final Vec3 center;
        final long expireTick;
        final float radius;
        final GloompurgeMarkEntity sigil;
        final GloompurgeMarkEntity edge;
        final Map<UUID, Long> lastEchoTick = new HashMap<>();

        Domain(Level level, Vec3 center, long expireTick, float radius,
               GloompurgeMarkEntity sigil, GloompurgeMarkEntity edge) {
            this.level = level;
            this.center = center;
            this.expireTick = expireTick;
            this.radius = radius;
            this.sigil = sigil;
            this.edge = edge;
        }
    }

    private static final Map<UUID, Domain> DOMAINS = new HashMap<>();

    // 蓄力中的打击

    private static final class PendingStrike {
        final ServerLevel level;
        final LivingEntity owner;
        final LivingEntity target;
        final Vec3 skyStart;
        final float damage;
        final long fireTick;

        PendingStrike(ServerLevel level, LivingEntity owner, LivingEntity target,
                      Vec3 skyStart, float damage, long fireTick) {
            this.level = level;
            this.owner = owner;
            this.target = target;
            this.skyStart = skyStart;
            this.damage = damage;
            this.fireTick = fireTick;
        }
    }

    private static final Deque<PendingStrike> PENDING = new ArrayDeque<>();

    // 展开领域（施法时调用，仅服务端）

    public static void openDomain(LivingEntity caster, int spellLevel) {
        long now = caster.level().getGameTime();
        // 半径随等级（14→22 格），时长随法术强度（L1=20s → L5=40s，受法强加成）
        float radius = GloompurgeSpell.getRadius(spellLevel);
        long duration = GloompurgeSpell.getDurationTicks(spellLevel, caster);

        // 圆心八卦阵（比例半径，缓慢旋转）
        GloompurgeMarkEntity sigil = new GloompurgeMarkEntity(
            EntityRegistry.GLOOMPURGE_MARK.get(), caster.level(), caster.position(),
            GloompurgeMarkEntity.SHAPE_DOMAIN_OCTAGON,
            radius * SIGIL_RADIUS_RATIO, 0f, (int) duration
        );
        caster.level().addFreshEntity(sigil);

        // 领域边缘：锯齿电波外环 + 反向虚线弧 + 刻度 + 扫描亮弧
        GloompurgeMarkEntity edge = new GloompurgeMarkEntity(
            EntityRegistry.GLOOMPURGE_MARK.get(), caster.level(), caster.position(),
            GloompurgeMarkEntity.SHAPE_DOMAIN_EDGE,
            radius, 0f, (int) duration
        );
        caster.level().addFreshEntity(edge);

        // 重复施法只保留一套视觉：丢弃旧印记后再登记新领域
        Domain old = DOMAINS.put(caster.getUUID(),
            new Domain(caster.level(), caster.position(), now + duration, radius, sigil, edge));
        if (old != null) {
            if (!old.sigil.isRemoved()) old.sigil.discard();
            if (!old.edge.isRemoved()) old.edge.discard();
        }
    }

    // 触发回声

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        var source = event.getSource();
        // 防递归：回声打击自身不触发新回声
        if (source instanceof SpellDamageSource sds && sds.spell() == SpellRegistry.GLOOMPURGE.get()) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        // 高射速武器一次命中分多次 hurt，0 伤害那次不得占用回声冷却
        if (event.getAmount() <= 0) return;

        Domain domain = DOMAINS.get(attacker.getUUID());
        if (domain == null) return;

        // 维度守卫：领域只在展开它的维度生效（中心坐标与 gameTime 基准都属于该维度）
        if (attacker.level() != domain.level) return;

        long now = domain.level.getGameTime();
        if (now >= domain.expireTick) {
            DOMAINS.remove(attacker.getUUID());
            return;
        }

        LivingEntity target = event.getEntity();
        // 自伤不触发回声：施法者永远站在自己领域内，攻击者==目标时跳过
        if (attacker == target) return;
        if (!inDomain(domain, target)) return;

        // 每目标短冷却：防高射速刷屏，总次数仍无限
        int cooldown = GloompurgeSpell.ECHO_COOLDOWN_TICKS;
        Long last = domain.lastEchoTick.get(target.getUUID());
        if (last != null && now - last < cooldown) return;
        domain.lastEchoTick.put(target.getUUID(), now);

        // 回声伤害 = 触发伤害 × 百分比 × 法术强度倍率
        AbstractSpell spell = SpellRegistry.GLOOMPURGE.get();
        float percent = GloompurgeSpell.ECHO_DAMAGE_PERCENT;
        float damage = event.getAmount() * percent * spell.getEntityPowerMultiplier(attacker);

        // 斜上方起点：随机方位角，与地面呈 60°（发射时再按目标实时位置定终点）
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() / 2, 0);
        double azimuth = attacker.level().getRandom().nextDouble() * Math.PI * 2;
        Vec3 horizontal = new Vec3(Math.cos(azimuth), 0, Math.sin(azimuth));
        Vec3 skyStart = targetCenter
            .add(horizontal.scale(Math.cos(STRIKE_ELEVATION) * STRIKE_DISTANCE))
            .add(0, Math.sin(STRIKE_ELEVATION) * STRIKE_DISTANCE, 0);

        // 蓄力三角印记（渲染器逐帧收缩汇聚），旋转角复用随机方位（渲染器按角度解释）
        GloompurgeMarkEntity tri = new GloompurgeMarkEntity(
            EntityRegistry.GLOOMPURGE_MARK.get(), serverLevel, skyStart,
            GloompurgeMarkEntity.SHAPE_CHARGE_TRIANGLE,
            CHARGE_TRI_RADIUS, (float) Math.toDegrees(azimuth), CHARGE_TICKS + 2
        );
        serverLevel.addFreshEntity(tri);

        PENDING.addLast(new PendingStrike(serverLevel, attacker, target, skyStart,
            damage, now + CHARGE_TICKS));
    }

    // 蓄力推进 + 发射

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 定期清扫（每秒一次）：
        // - 过期领域立即移除（原先依赖下次触发伤害/玩家 tick 懒清理）；
        // - AI 施法者（非玩家）死亡/移除/区块卸载后同步清理条目，防止泄漏 —— 玩家由
        //   onPlayerLoggedOut 兜底；玩家死亡期间实体仍在，领域保持生效的原语义不变
        long globalTime = event.getServer().overworld().getGameTime();
        if (globalTime % 20 == 0 && !DOMAINS.isEmpty()) {
            DOMAINS.entrySet().removeIf(entry -> {
                Domain d = entry.getValue();
                if (d.level.getGameTime() >= d.expireTick) {
                    return true;
                }
                boolean casterOnline = d.level.getServer() != null
                    && d.level.getServer().getPlayerList().getPlayer(entry.getKey()) != null;
                if (casterOnline) {
                    return false;
                }
                return !(d.level instanceof ServerLevel sl
                    && sl.getEntity(entry.getKey()) instanceof LivingEntity le
                    && le.isAlive());
            });
        }

        if (PENDING.isEmpty()) return;

        Iterator<PendingStrike> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingStrike s = it.next();
            long now = s.level.getGameTime();
            if (now >= s.fireTick) {
                launch(s);
                it.remove();
            } else if (now % 2 == 0) {
                chargeVfx(s, now);
            }
        }
    }

    // 蓄力演出：沿打击路径下落的青白拖尾（三角收缩由印记实体渲染器承担）

    private static void chargeVfx(PendingStrike s, long now) {
        if (!s.target.isAlive()) return;
        float progress = Math.min(1f, Math.max(0f, 1f - (s.fireTick - now) / (float) CHARGE_TICKS));
        Vec3 tc = s.target.position().add(0, s.target.getBbHeight() / 2, 0);
        Vec3 p = s.skyStart.lerp(tc, progress);
        // DUST 无重力、颜色自定义；UNSTABLE_ENDER 是末影紫，与本法术青白色系不符
        var trail = new DustParticleOptions(
            new Vector3f(BLAST_TINT[0], BLAST_TINT[1], BLAST_TINT[2]), 0.8f);
        MagicManager.spawnParticles(s.level, trail,
            p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.01, true);
    }

    // 发射：blast 光束 + 环形波纹 + 飞溅 + starfall 彗星命中音 + 即时单体结算

    private static void launch(PendingStrike s) {
        Vec3 targetCenter = s.target.isAlive()
            ? s.target.position().add(0, s.target.getBbHeight() / 2, 0)
            : s.skyStart.add(0, -STRIKE_DISTANCE, 0);
        Vec3 dir = targetCenter.subtract(s.skyStart).normalize();

        // lizhi_yan 同款 blast 光束（青白色），束身仍指向目标中心
        var blast = new LizhiYanBlastVisualEntity(
            EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
            s.level, s.skyStart, targetCenter, dir, BLAST_TINT
        );
        s.level.addFreshEntity(blast);

        // 命中特效落在目标底部：环形波纹 + 飞溅 + 音效
        Vec3 feet = s.target.isAlive()
            ? s.target.position().add(0, 0.1, 0)
            : targetCenter;
        // 命中环形波纹（Comet.impactParticles 同款，青白色）
        MagicManager.spawnParticles(s.level,
            new BlastwaveParticleOptions(new Vector3f(BLAST_TINT[0], BLAST_TINT[1], BLAST_TINT[2]), 1.25f),
            feet.x, feet.y, feet.z, 1, 0, 0, 0, 0, true);
        // 命中飞溅（LightningStrike 同款火花参数，青白色）
        MagicManager.spawnParticles(s.level,
            new SparkParticleOptions(new Vector3f(BLAST_TINT[0], BLAST_TINT[1], BLAST_TINT[2])),
            feet.x, feet.y, feet.z, 25, 0.2f, 0.2f, 0.2f, 0.25f, true);

        // starfall 彗星命中音（Comet.doImpactSound 参数）
        s.level.playSound(null, feet.x, feet.y, feet.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL,
            0.8f, 1.35f + s.level.getRandom().nextFloat() * 0.3f);

        // 即时单体结算，清无敌帧保证不吃触发伤害的 iFrames
        if (s.target.isAlive() && s.owner.isAlive()) {
            s.target.invulnerableTime = 0;
            DamageSources.applyDamage(s.target, s.damage,
                SpellRegistry.GLOOMPURGE.get().getDamageSource(blast, s.owner));
        }
    }

    // 过期清理

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Domain domain = DOMAINS.get(player.getUUID());
        if (domain == null) return;
        if (domain.level.getGameTime() >= domain.expireTick) {
            DOMAINS.remove(player.getUUID());
        }
    }

    // 登出清理

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DOMAINS.remove(event.getEntity().getUUID());
    }

    // 工具

    private static boolean inDomain(Domain domain, LivingEntity target) {
        Vec3 d = target.position().subtract(domain.center);
        // 垂直容差 5 格，水平距离判定圆形领域
        if (Math.abs(d.y) > 5.0) return false;
        return Math.sqrt(d.x * d.x + d.z * d.z) <= domain.radius;
    }
}

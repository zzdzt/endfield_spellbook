package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.particle.InkZapParticleOption;
import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.particle.SparkParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * 青霆剑诀雷击演出调度与参数（全部占位，进游戏后统一调）。
 *
 * <p>时序（对齐 ArcaneMag 惊霆诀定版 V2）：首击水墨雷瀑（瀑布雷柱）→
 * 持续期每柄剑错峰剑位水墨雷 + 目标位轮劈（tier≥3 加红闪）→ 收尾红芯雷瀑 + 墨黑晶刺放射。
 * tier = 剑数档（0~4，剑数/2），驱动副瀑布数量与红闪。
 *
 * <p>调度：静态延迟队列（GloompurgeEventHandler 同款），ServerTick 驱动。
 * 伤害结算由法术层以同款队列排入（演出帧 = 命中帧）。
 */
@EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID)
public final class ThunderStrikeHelper {

    private ThunderStrikeHelper() {
    }

    // ==================== 参数占位（全部待调） ====================

    /** 单道水墨雷的天际行程。 */
    public static final float BOLT_HEIGHT = 25f;
    /** 首击雷瀑柱高。 */
    public static final float FIRST_HEIGHT = 24f;
    /** 首击副瀑散布半径。 */
    public static final float FIRST_SPREAD = 2.0f;
    /** 首击侧雷数量。 */
    public static final int FIRST_SIDE_BOLTS = 2;
    /** 收尾雷瀑柱高。 */
    public static final float FINALE_HEIGHT = 26f;
    /** 收尾副瀑散布半径。 */
    public static final float FINALE_SPREAD = 2.6f;
    /** 收尾墨黑晶刺放射（Spark）。 */
    public static final int FINALE_SPARKS = 30;
    /** 每次雷击的墨屑数量。 */
    public static final int INK_CHUNKS = 24;

    private static final Vector3f COL_INK = new Vector3f(0.06f, 0.06f, 0.08f);
    private static final Vector3f COL_CYAN_WHITE = new Vector3f(0.70f, 0.95f, 1.00f);
    private static final Vector3f COL_CYAN = new Vector3f(0.31f, 0.85f, 1.00f);

    // ==================== 延迟队列 ====================

    private record PendingStrike(Runnable action, long fireTick, ServerLevel level) {
    }

    private static final Deque<PendingStrike> QUEUE = new ArrayDeque<>();

    /** 延迟执行演出/结算动作。 */
    public static void schedule(ServerLevel level, long delayTicks, Runnable action) {
        QUEUE.addLast(new PendingStrike(action, level.getGameTime() + delayTicks, level));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;
        Iterator<PendingStrike> it = QUEUE.iterator();
        while (it.hasNext()) {
            PendingStrike p = it.next();
            if (p.level().getGameTime() >= p.fireTick()) {
                p.action().run();
                it.remove();
            }
        }
    }

    // ==================== 高层 API（法术调用） ====================

    /** 首击：水墨雷瀑（瀑布雷柱，tier≥3 红芯）+ 侧雷点缀 + 落点爆发。 */
    public static void scheduleFirstStrike(ServerLevel level, Vec3 targetPos, int tier, long delay) {
        schedule(level, delay, () -> {
            RandomSource rand = level.getRandom();
            level.addFreshEntity(new InkWaterfallEntity(
                EntityRegistry.INK_WATERFALL.get(), level, targetPos,
                FIRST_HEIGHT, FIRST_SPREAD, tier));
            // 侧雷（小幅散开，锯齿水墨雷点缀）
            for (int i = 0; i < FIRST_SIDE_BOLTS; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 0.8 + rand.nextDouble() * 1.0;
                spawnInkBolt(level, targetPos.add(Math.cos(ang) * dist, 0.1, Math.sin(ang) * dist), 1.0f, rand);
            }
            MagicManager.spawnParticles(level, ParticleTypes.FLASH,
                targetPos.x, targetPos.y + 0.5, targetPos.z,
                1, 0.2, 0.3, 0.2, 0, true);
            MagicManager.spawnParticles(level, new BlastwaveParticleOptions(COL_CYAN, 1.2f),
                targetPos.x, targetPos.y + 0.1, targetPos.z, 1, 0, 0, 0, 0, true);
            spawnInkBurst(level, targetPos);
            level.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.2f);
        });
    }

    /** 剑的雷击：剑位水墨雷 + 目标位水墨雷同时落下（同 tick 双雷）+ 落点墨屑。 */
    public static void scheduleBladeStrike(ServerLevel level, Vec3 bladePos, Vec3 targetPos,
                                           boolean inkFlash, int tier, long delay) {
        schedule(level, delay, () -> {
            // 剑位：单道水墨雷（剑引天雷）
            spawnInkBolt(level, bladePos.add(0, 0.1, 0), 1.0f, level.getRandom());
            // 目标位：同 tick 一道完整水墨雷（宽度随轮劈微变）
            spawnInkBolt(level, targetPos.add(0, 0.1, 0), inkFlash ? 1.0f : 0.85f, level.getRandom());
            spawnInkBurst(level, targetPos);
            level.playSound(null, bladePos.x, bladePos.y, bladePos.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35f, 1.6f);
        });
    }

    /** 收尾：红芯雷瀑 + 红闪（tier≥3，红闪仅出现在收尾）+ 墨黑晶刺放射 + 双音效（爆炸 + 雷声）。
     *  火帧时再次定向目标当前位置；目标已死/移除则退回 fallbackPos。 */
    public static void scheduleFinale(ServerLevel level, LivingEntity target, Vec3 fallbackPos, int tier, long delay) {
        schedule(level, delay, () -> {
            RandomSource rand = level.getRandom();
            Vec3 targetPos = target.isAlive() ? target.position() : fallbackPos;
            level.addFreshEntity(new InkWaterfallEntity(
                EntityRegistry.INK_WATERFALL.get(), level, targetPos,
                FINALE_HEIGHT, FINALE_SPREAD, tier));
            // 红闪（tier≥3，AM 定版红色闪电规格）
            if (tier >= 3) {
                spawnRedBolt(level, targetPos, rand);
                spawnRedBolt(level, targetPos, rand);
            }
            MagicManager.spawnParticles(level, ParticleTypes.FLASH,
                targetPos.x, targetPos.y + 0.5, targetPos.z,
                2, FINALE_SPREAD * 0.3, 0.5, FINALE_SPREAD * 0.3, 0, true);
            // 墨黑碎晶放射（星形 Spark，白闪后渐变墨黑）+ 亮白冲击环
            MagicManager.spawnParticles(level,
                new SparkParticleOptions(COL_INK),
                targetPos.x, targetPos.y + 0.4, targetPos.z,
                FINALE_SPARKS, 0.35f, 0.5f, 0.35f, 0.45f, true);
            MagicManager.spawnParticles(level,
                new BlastwaveParticleOptions(COL_CYAN_WHITE, 1.6f),
                targetPos.x, targetPos.y + 0.1, targetPos.z, 1, 0, 0, 0, 0, true);
            spawnInkBurst(level, targetPos);
            level.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.35f + rand.nextFloat() * 0.3f);
            level.playSound(null, targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 0.7f + rand.nextFloat() * 0.3f);
        });
    }

    // ==================== 演出原语 ====================

    /**
     * 单道水墨雷（惊霆诀原版观感）：焦墨/苍青/亮白三层，
     * 底→中→顶两段链 + 拐点随机分叉——全部由单个 InkZap 粒子内部渲染（1 包 = 1 雷）。
     */
    private static void spawnInkBolt(ServerLevel level, Vec3 bottom, float widthScale, RandomSource rand) {
        Vec3 top = bottom.add(
            (rand.nextDouble() - 0.5) * 3,
            BOLT_HEIGHT + rand.nextFloat() * 5,
            (rand.nextDouble() - 0.5) * 3);
        Vec3 middle = bottom.add(
            (rand.nextDouble() - 0.5) * 2,
            (top.y - bottom.y) * (0.3 + rand.nextFloat() * 0.4),
            (rand.nextDouble() - 0.5) * 2);

        MagicManager.spawnParticles(level, new InkZapParticleOption(middle, top, false, widthScale),
            bottom.x, bottom.y, bottom.z, 1, 0, 0, 0, 0, true);
    }

    /**
     * 红色闪电（tier≥3 充能视觉，AM 定版同款）：
     * 红外层 0.22 + 红中层 0.14 + 白热核心 0.08，规格 ×1.2——单粒子整雷。
     */
    private static void spawnRedBolt(ServerLevel level, Vec3 center, RandomSource rand) {
        double ang = rand.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rand.nextDouble()) * 1.5;
        Vec3 bottom = center.add(Math.cos(ang) * dist, 0.1, Math.sin(ang) * dist);
        Vec3 top = bottom.add(
            (rand.nextDouble() - 0.5) * 3,
            BOLT_HEIGHT + rand.nextFloat() * 5,
            (rand.nextDouble() - 0.5) * 3);
        Vec3 middle = bottom.add(
            (rand.nextDouble() - 0.5) * 2,
            (top.y - bottom.y) * (0.3 + rand.nextFloat() * 0.4),
            (rand.nextDouble() - 0.5) * 2);

        MagicManager.spawnParticles(level, new InkZapParticleOption(middle, top, true, 1.2f),
            bottom.x, bottom.y, bottom.z, 1, 0, 0, 0, 0, true);
    }

    /** 落点爆发：水墨黑碎屑爆散（装饰性粒子，不 force，尊重玩家粒子设置）。 */
    private static void spawnInkBurst(ServerLevel level, Vec3 targetPos) {
        MagicManager.spawnParticles(level,
            new DustParticleOptions(COL_INK, 0.35f),
            targetPos.x, targetPos.y + 0.5, targetPos.z,
            INK_CHUNKS, 0.4f, 0.3f, 0.4f, 0.35f, false);
    }
}

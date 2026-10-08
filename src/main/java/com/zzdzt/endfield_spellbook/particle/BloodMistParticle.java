package com.zzdzt.endfield_spellbook.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import org.joml.Vector3f;

/**
 * 血雾（衔火血翼体积层）：单一类 + Kind 参数区分三档雾行为，不建多个雾粒子类。
 *
 * <ul>
 *   <li><b>CORE</b> 贴身暗芯：小、密度高、近乎随翼——翼身的"厚度"</li>
 *   <li><b>OUTER</b> 外围淡胀：大、淡、缓慢膨胀消散——体积的蓬松边缘</li>
 *   <li><b>TRAIL</b> 轨迹残影：低透明度、近零初速——生成在轨迹上自然滞后，
 *       急转弯时旧雾留在原地形成弧形残影（不记录历史轨迹，第一版原则）</li>
 * </ul>
 *
 * <p>半透明软斑渲染（PARTICLE_SHEET_TRANSLUCENT）；alpha 二次缓出（先驻留后消散）；
 * 逐 tick 膨胀由 baseSize × 成长系数驱动；出速极低，运动感由生成位置承担。
 * 装饰粒子（overrideLimiter=false），低画质可被裁剪——血萤主体才是 overrideLimiter=true。
 */
public class BloodMistParticle extends TextureSheetParticle {

    public enum Kind { CORE, OUTER, TRAIL }

    // ===== Kind 外观参数表：基础尺寸 / 每 tick 成长 / 初始 alpha / 寿命基准(t) / 顶点色 =====
    private static final float[] SIZE = {0.35f, 0.70f, 0.25f};
    private static final float[] GROWTH = {0.15f, 0.60f, 0.40f};
    private static final float[] ALPHA = {0.45f, 0.22f, 0.18f};
    private static final int[] LIFE = {16, 26, 36};
    private static final Vector3f[] TINT = {
        new Vector3f(0.45f, 0.05f, 0.09f),   // CORE 暗血芯
        new Vector3f(0.55f, 0.12f, 0.16f),   // OUTER 淡红缘
        new Vector3f(0.35f, 0.04f, 0.08f)    // TRAIL 深红残影
    };

    private final int kindIndex;
    private final float baseSize;
    private final float baseAlpha;

    BloodMistParticle(ClientLevel level, double x, double y, double z,
                      double xd, double yd, double zd, Kind kind, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.kindIndex = kind.ordinal();
        int i = kindIndex;
        this.baseSize = SIZE[i] * (0.85f + this.random.nextFloat() * 0.3f); // 个体尺寸差异防"克隆雾"
        this.baseAlpha = ALPHA[i];
        this.lifetime = LIFE[i] + this.random.nextInt(Math.max(1, LIFE[i] / 2)); // 寿命抖动防齐闪灭
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.quadSize = baseSize;
        this.setSprite(sprites.get(0, 1));
        this.alpha = baseAlpha;
        this.rCol = TINT[i].x();
        this.gCol = TINT[i].y();
        this.bCol = TINT[i].z();
    }

    @Override
    public void tick() {
        // 前 2 tick 快速淡入防"凭空出现"，之后 alpha 二次缓出（驻留感）
        float t = age / (float) lifetime;
        float fadeIn = Math.min(1f, age / 2f);
        this.alpha = baseAlpha * fadeIn * (1f - t * t);
        // 缓慢膨胀
        this.quadSize = baseSize * (1f + GROWTH[kindIndex] * t);
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new BloodMistParticle(level, x, y, z, dx, dy, dz, kind, sprites);
        }
    }
}

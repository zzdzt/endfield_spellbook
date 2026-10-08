package com.zzdzt.endfield_spellbook.particle;

import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * 血萤（驱火焚影 V1 视觉主体）：ISS FireflyParticle 同构换色——
 * 贴图为单像素血红光点（16×16 画布 1 不透明像素，ISS firefly 同款结构），
 * 顶点色承载明灭：亮态纯白（贴图色直出）/ 暗态近黑。
 * 双态闪烁 + 游荡力场（random^4 偏斜 + 逐帧衰减）。
 *
 * <p>⚠️ 与 ISS 原版的刻意偏差：寿命大幅缩短（10~24t vs 20~110t）且**不保留**
 * "亮着不许死"规则——ISS 那套是"氛围萤火虫"参数（粒子长期漂浮）；追猎群体
 * 需要快速周转：主体 = 每 tick 新生成的粒子群，尾迹靠短寿命自然收干，
 * 主体位置因此始终可读。快速闪烁（3~10t 翻转）匹配快周转节奏。
 *
 * <p>OPAQUE 硬边 + 永远全亮。
 */
public class BloodFireflyParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private boolean lit;
    private float litTween;
    private int litTimer;
    private float wander;
    private final float flickerIntensity;

    private static final Vector3f LIT_COLOR = new Vector3f(1f, 1f, 1f);
    private static final Vector3f UNLIT_COLOR = new Vector3f(22 / 255f, 20 / 255f, 18 / 255f);

    public BloodFireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet, double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.scale(2.5f);
        this.lifetime = 10 + (int) (Math.random() * 14);
        this.sprites = spriteSet;
        this.gravity = 0F;
        lit = Utils.random.nextBoolean();
        litTween = lit ? 1 : 0;
        wander = Utils.random.nextFloat() * 2.5f;
        wander *= wander * wander * wander;
        this.setSprite(sprites.get(0, 1));
        this.rCol = 1f;
        this.gCol = 1f;
        this.bCol = 1f;
        this.flickerIntensity = Utils.random.nextIntBetweenInclusive(18, 45) * .01f;
    }

    @Override
    public void tick() {
        // 游荡力场：抖动力正比 wander（构造期已 random^4 偏斜），逐帧衰减
        float xj = (this.random.nextFloat() * .001f * wander * (this.random.nextBoolean() ? 1 : -1));
        float yj = (this.random.nextFloat() * .001f * wander * (this.random.nextBoolean() ? 1 : -1)) + .00025f;
        float zj = (this.random.nextFloat() * .001f * wander * (this.random.nextBoolean() ? 1 : -1));
        wander *= .98f;
        this.xd += xj;
        this.yd += yj;
        this.zd += zj;

        // 双态明灭：亮/暗快速翻转（3~10t），颜色按 litTween 插值
        if (--litTimer <= 0) {
            lit = !lit;
            litTimer = random.nextIntBetweenInclusive(3, 10);
        }
        if (lit) {
            litTween = Mth.lerp(flickerIntensity, litTween, 1);
        } else {
            litTween = Mth.lerp(flickerIntensity, litTween, 0);
        }
        this.rCol = Mth.lerp(litTween, UNLIT_COLOR.x(), LIT_COLOR.x());
        this.gCol = Mth.lerp(litTween, UNLIT_COLOR.y(), LIT_COLOR.y());
        this.bCol = Mth.lerp(litTween, UNLIT_COLOR.z(), LIT_COLOR.z());

        // 注意：不保留 ISS 的"亮着不许死"规则——追猎群体需要稳定消散节奏
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    protected int getLightColor(float pPartialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet spriteSet) {
            this.sprites = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType particleType, ClientLevel level,
                                       double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new BloodFireflyParticle(level, x, y, z, this.sprites, dx, dy, dz);
        }
    }
}

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
 * 保留双态闪烁 + 游荡力场（random^4 偏斜 + 逐帧衰减）+ "亮着不许死"规则。
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
        this.lifetime = 20 + (int) (Math.random() * 90);
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

        // 双态明灭：亮/暗随机翻转，颜色按 litTween 插值
        if (--litTimer <= 0) {
            lit = !lit;
            litTimer = random.nextIntBetweenInclusive(5, 20);
        }
        if (lit) {
            litTween = Mth.lerp(flickerIntensity, litTween, 1);
        } else {
            litTween = Mth.lerp(flickerIntensity, litTween, 0);
        }
        this.rCol = Mth.lerp(litTween, UNLIT_COLOR.x(), LIT_COLOR.x());
        this.gCol = Mth.lerp(litTween, UNLIT_COLOR.y(), LIT_COLOR.y());
        this.bCol = Mth.lerp(litTween, UNLIT_COLOR.z(), LIT_COLOR.z());

        // "亮着不许死"：只有淡出到暗态才允许消亡
        if (age >= lifetime - 1 && litTween > .1f) {
            lifetime++;
        }
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

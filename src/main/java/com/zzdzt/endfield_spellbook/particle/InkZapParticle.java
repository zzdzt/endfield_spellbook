package com.zzdzt.endfield_spellbook.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 水墨闪电粒子：单粒子渲染一整道雷。
 *
 * 一个包（{@link InkZapParticleOption}）承载 落点(粒子位置)→拐点→顶点 两段，
 * 内部按三层宽度（焦墨外层/苍青中层/亮白核心，朱红雷为 红外/红中/白热核）
 * 逐层渲染，每层固定种子随机——同帧三层形状一致，跨帧闪烁。
 * 网络侧 1 包 = 1 雷（旧版为 3 层 × 2 段 + 分叉共 6-7 包）。
 */
public class InkZapParticle extends TextureSheetParticle {

    private final Vec3 middle;
    private final Vec3 top;
    private final boolean red;
    private final float scale;

    // 颜色定义（tier 索引）：0=焦墨, 1=苍青, 2=亮白, 3=朱红
    private static final float[] INK_JIAO = {0.02f, 0.02f, 0.02f, 0.92f};
    private static final float[] CANG_QING = {0.35f, 0.72f, 0.68f, 0.75f};
    private static final float[] LIANG_BAI = {0.85f, 0.96f, 0.94f, 0.95f};
    private static final float[] ZHU_HONG = {0.86f, 0.24f, 0.20f, 0.90f};

    private static final float[][] COLORS = {
        INK_JIAO,
        CANG_QING,
        LIANG_BAI,
        ZHU_HONG
    };

    /** 水墨雷三层：{基准宽度, 颜色 tier}。 */
    private static final float[][] INK_PASSES = {{0.25f, 0}, {0.18f, 1}, {0.08f, 2}};
    /** 朱红雷三层（AM 定版规格，×1.2 由 option.scale 携带）。 */
    private static final float[][] RED_PASSES = {{0.22f, 3}, {0.14f, 3}, {0.08f, 2}};

    InkZapParticle(ClientLevel level, double x, double y, double z,
                   double xd, double yd, double zd, InkZapParticleOption options) {
        super(level, x, y, z, 0, 0, 0);
        this.setSize(1, 1);
        this.quadSize = 1f;
        this.middle = options.getMiddle();
        this.top = options.getTop();
        this.red = options.isRed();
        this.scale = options.getScale();
        this.lifetime = Utils.random.nextIntBetweenInclusive(3, 8);
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public Vec3 randomOffset(RandomSource random, float scale) {
        return new Vec3(
            (2f * random.nextFloat() - 1f) * scale,
            (2f * random.nextFloat() - 1f) * scale,
            (2f * random.nextFloat() - 1f) * scale
        );
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        Vec3 vec3 = camera.getPosition();
        float f = (float) (Mth.lerp((double) partialTick, this.xo, this.x) - vec3.x());
        float f1 = (float) (Mth.lerp((double) partialTick, this.yo, this.y) - vec3.y());
        float f2 = (float) (Mth.lerp((double) partialTick, this.zo, this.z) - vec3.z());
        PoseStack poseStack = new PoseStack();
        poseStack.translate(f, f1, f2);

        Vec3 midRel = this.middle.subtract(this.getPos());
        Vec3 topRel = this.top.subtract(this.getPos());
        if (midRel.length() < 0.1 && topRel.length() < 0.1) return;

        float[][] passes = red ? RED_PASSES : INK_PASSES;
        for (int p = 0; p < passes.length; p++) {
            float width = passes[p][0] * scale;
            float[] color = COLORS[(int) passes[p][1]];
            // 每 pass 固定种子：同帧三层形状一致（各层错开种子，锯齿独立）
            RandomSource rng = RandomSource.create((age + lifetime) * 3456798L + p * 9176L);
            renderPolyline(consumer, poseStack, partialTick, Vec3.ZERO, midRel, width, color, 0.25f, rng);
            renderPolyline(consumer, poseStack, partialTick, midRel, topRel, width, color, 0.25f, rng);
            // 首层的拐点随机分叉（70%，seeded，同帧一致）
            if (p == 0 && rng.nextFloat() < 0.7f) {
                Vec3 split = midRel.add(randomOffset(rng, 1.5f).multiply(1, 0.4, 1));
                float[] branchColor = COLORS[red ? 3 : 1];
                renderPolyline(consumer, poseStack, partialTick, midRel, split, 0.12f * scale, branchColor, 0.25f, rng);
            }
        }
    }

    /** 一段折线：按距离分段 + 锯齿抖动 + 递归分叉（逐层传递颜色）。 */
    private void renderPolyline(VertexConsumer consumer, PoseStack poseStack, float partialTick,
                                 Vec3 start, Vec3 end, float width, float[] color,
                                 float chanceToBranch, RandomSource rng) {
        double distance = end.subtract(start).length();
        if (distance < 0.1) return;

        // 限制分段数量，以防止在极端距离下发生 BufferBuilder 溢出
        int segments = (int) Math.min(distance / 2.5 + rng.nextIntBetweenInclusive(2, 4), 256);
        double distancePerSegment = distance / segments;
        Vec3 direction = end.subtract(start).normalize();

        Vec3 cursor = start;
        for (int i = 0; i < segments; i++) {
            // 水平摆动更大，覆盖范围感
            Vec3 wiggle = randomOffset(rng, 0.4f);
            // 水平方向的闪电段增加更多水平偏移
            if (Math.abs(end.y - start.y) < 5.0) {
                wiggle = wiggle.add(
                    (2f * rng.nextFloat() - 1f) * 1.5,
                    0,
                    (2f * rng.nextFloat() - 1f) * 1.5
                );
            }
            Vec3 segmentEnd = cursor.add(direction.scale(distancePerSegment)).add(wiggle);
            drawLightningBeam(consumer, poseStack, partialTick, cursor, segmentEnd, width, chanceToBranch, color, rng);
            cursor = segmentEnd;
        }
    }

    private void drawLightningBeam(VertexConsumer consumer, PoseStack poseStack, float partialTick,
                                    Vec3 start, Vec3 end, float width, float chanceToBranch,
                                    float[] color, RandomSource randomSource) {
        drawTube(consumer, poseStack, start, end, width, color);

        // 分叉
        if (randomSource.nextFloat() < chanceToBranch) {
            Vec3 branch = randomOffset(randomSource, 1.2f).add(end);
            drawLightningBeam(consumer, poseStack, partialTick, end, branch, width * 0.7f, chanceToBranch * 0.5f, color, randomSource);
        }
    }

    private void drawTube(VertexConsumer consumer, PoseStack poseStack,
                          Vec3 start, Vec3 end, float width, float[] color) {
        Vec3 delta = end.subtract(start);
        float length = (float) delta.length();
        if (length <= 1e-6f) return;

        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        Vec2 rotation = Utils.rotationFromDirection(delta.normalize());
        poseStack.mulPose(Axis.YP.rotation(rotation.y));
        poseStack.mulPose(Axis.XP.rotation(-rotation.x));
        drawHull(Vec3.ZERO, new Vec3(0, 0, length), width, width, poseStack, consumer, color);
        poseStack.popPose();
    }

    private void drawHull(Vec3 from, Vec3 to, float width, float height,
                          PoseStack poseStack, VertexConsumer consumer, float[] color) {
        poseStack.pushPose();
        for (int i = 0; i < 4; i++) {
            drawQuad(from.subtract(0, height * .5f, 0), to.subtract(0, height * .5f, 0),
                width, 0, poseStack.last(), consumer, color);
            poseStack.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
        }
        poseStack.popPose();
    }

    private void drawQuad(Vec3 from, Vec3 to, float width, float height,
                          PoseStack.Pose pose, VertexConsumer consumer, float[] color) {
        Matrix4f poseMatrix = pose.pose();
        float halfWidth = width * .5f;
        int light = getLightColor(1.0f);
        int r = (int) (color[0] * 255);
        int g = (int) (color[1] * 255);
        int b = (int) (color[2] * 255);
        int a = (int) (color[3] * 255);

        consumer.vertex(poseMatrix, (float) from.x - halfWidth, (float) from.y, (float) from.z).uv(getU1(), getV1()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) from.x + halfWidth, (float) from.y, (float) from.z).uv(getU1(), getV0()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) to.x + halfWidth, (float) to.y, (float) to.z).uv(getU0(), getV0()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) to.x - halfWidth, (float) to.y, (float) to.z).uv(getU0(), getV1()).color(r, g, b, a).uv2(light).endVertex();
    }

    @NotNull
    @Override
    public ParticleRenderType getRenderType() {
        return INK_PARTICLE_BLEND;
    }

    public static ParticleRenderType INK_PARTICLE_BLEND = new ParticleRenderType() {
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        public void end(Tesselator tesselator) {
            tesselator.end();
        }
        public String toString() {
            return "INK_PARTICLE_BLEND";
        }
    };

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<InkZapParticleOption> {
        private final SpriteSet sprite;
        public Provider(SpriteSet pSprite) {
            this.sprite = pSprite;
        }
        public Particle createParticle(@NotNull InkZapParticleOption options, @NotNull ClientLevel pLevel,
                                      double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed) {
            var particle = new InkZapParticle(pLevel, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed, options);
            particle.pickSprite(this.sprite);
            return particle;
        }
    }
}

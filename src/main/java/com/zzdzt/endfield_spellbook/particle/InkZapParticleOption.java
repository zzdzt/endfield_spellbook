package com.zzdzt.endfield_spellbook.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.stream.IntStream;

/**
 * 水墨闪电粒子参数：一个包承载一整道雷。
 *
 * 字段：拐点 middle + 顶点 top（世界坐标，粒子生成于落点 bottom）+
 * red（false=水墨雷 焦墨/苍青/亮白，true=朱红雷 红外/红中/白热核）+
 * scale（整雷宽度倍率）。
 *
 * 旧版每道雷拆 6-7 个 count=1 包（3 层 × 2 段 + 分叉）逐个广播；
 * 现由 {@link InkZapParticle} 单粒子内部渲染全部层与段，1 包 = 1 雷。
 */
public class InkZapParticleOption implements ParticleOptions {

    // 编码：middle×3 + top×3（各 ×10 取整）+ red(0/1) + scale(×100) = 8 个 int
    public static final Codec<InkZapParticleOption> CODEC = Codec.INT_STREAM.comapFlatMap(
        (stream) -> Util.fixedSize(stream, 8).map((arr) ->
            new InkZapParticleOption(
                new Vec3(arr[0] / 10f, arr[1] / 10f, arr[2] / 10f),
                new Vec3(arr[3] / 10f, arr[4] / 10f, arr[5] / 10f),
                arr[6] != 0,
                arr[7] / 100f
            )
        ),
        (option) -> IntStream.of(
            enc(option.middle.x), enc(option.middle.y), enc(option.middle.z),
            enc(option.top.x), enc(option.top.y), enc(option.top.z),
            option.red ? 1 : 0,
            (int) (option.scale * 100)
        )
    );

    public static final Deserializer<InkZapParticleOption> DESERIALIZER =
        new Deserializer<InkZapParticleOption>() {

            @Override
            public InkZapParticleOption fromCommand(ParticleType<InkZapParticleOption> type,
                                                     StringReader reader) throws CommandSyntaxException {
                reader.expect(' ');
                double mx = reader.readDouble();
                reader.expect(' ');
                double my = reader.readDouble();
                reader.expect(' ');
                double mz = reader.readDouble();
                reader.expect(' ');
                double tx = reader.readDouble();
                reader.expect(' ');
                double ty = reader.readDouble();
                reader.expect(' ');
                double tz = reader.readDouble();
                reader.expect(' ');
                boolean red = reader.readInt() != 0;
                reader.expect(' ');
                float scale = (float) reader.readDouble();
                return new InkZapParticleOption(new Vec3(mx, my, mz), new Vec3(tx, ty, tz), red, scale);
            }

            @Override
            public InkZapParticleOption fromNetwork(ParticleType<InkZapParticleOption> type,
                                                     FriendlyByteBuf buf) {
                return new InkZapParticleOption(
                    readVec3FromNetwork(buf),
                    readVec3FromNetwork(buf),
                    buf.readBoolean(),
                    buf.readFloat()
                );
            }
        };

    private final Vec3 middle;
    private final Vec3 top;
    private final boolean red;
    private final float scale;

    public InkZapParticleOption(Vec3 middle, Vec3 top, boolean red, float scale) {
        this.middle = middle;
        this.top = top;
        this.red = red;
        this.scale = Math.max(0.05f, scale);
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        writeVec3ToNetwork(this.middle, buf);
        writeVec3ToNetwork(this.top, buf);
        buf.writeBoolean(this.red);
        buf.writeFloat(this.scale);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f %.2f %.2f %s %.2f",
            BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()),
            middle.x, middle.y, middle.z, top.x, top.y, top.z, red, scale);
    }

    @Override
    public ParticleType<InkZapParticleOption> getType() {
        return com.zzdzt.endfield_spellbook.registry.ParticleRegistry.INK_ZAP_PARTICLE.get();
    }

    public Vec3 getMiddle() {
        return this.middle;
    }

    public Vec3 getTop() {
        return this.top;
    }

    public boolean isRed() {
        return this.red;
    }

    public float getScale() {
        return this.scale;
    }

    private static int enc(double v) {
        return (int) (v * 10f);
    }

    private static Vec3 readVec3FromNetwork(FriendlyByteBuf buf) {
        return new Vec3(buf.readInt() / 10f, buf.readInt() / 10f, buf.readInt() / 10f);
    }

    private static void writeVec3ToNetwork(Vec3 vec, FriendlyByteBuf buf) {
        buf.writeInt((int) (vec.x * 10));
        buf.writeInt((int) (vec.y * 10));
        buf.writeInt((int) (vec.z * 10));
    }
}

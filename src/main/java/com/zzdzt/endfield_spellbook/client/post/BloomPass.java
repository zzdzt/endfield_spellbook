package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * 自控泛光 pass：两级降采样 ping-pong 高斯。
 *
 *   CA0(特效层) → 直通降采样 0.5x → H/V ×2 → 直通降采样 0.25x → H/V ×2
 *              → 远景柔光加法叠回近景（glBlendFunc(ONE, ONE)）
 *
 * 近景层保留锐利辉光，远景层提供大范围柔和光晕，两层叠加接近终末地的
 * "能量泛光"质感。全流程无深度附件、加法合成，性能预算 ~0.5ms @1080p。
 * 输出近景 FBO 纹理（0.5x），由 FinalPass 线性采样放大合成。
 */
public final class BloomPass implements AutoCloseable {

    private static final float NEAR_RADIUS = 1.35f;
    private static final float FAR_RADIUS = 2.0f;
    private static final int NEAR_PASSES = 2;
    private static final int FAR_PASSES = 2;

    private PostShaders.Program blurProgram;
    private ArcaneFBO nearPing;
    private ArcaneFBO nearPong;
    private ArcaneFBO farPing;
    private ArcaneFBO farPong;
    private final FullscreenQuad quad;

    public BloomPass(FullscreenQuad quad) {
        this.quad = quad;
    }

    public void init(int width, int height) {
        if (blurProgram != null) {
            return;
        }
        blurProgram = PostShaders.load("bloom_blur");
        nearPing = createHalf(width, height, 1);
        nearPong = createHalf(width, height, 1);
        farPing = createHalf(width, height, 2);
        farPong = createHalf(width, height, 2);
    }

    private static ArcaneFBO createHalf(int width, int height, int shift) {
        ArcaneFBO fbo = new ArcaneFBO(1);
        fbo.create(Math.max(1, width >> shift), Math.max(1, height >> shift));
        return fbo;
    }

    public void resize(int width, int height) {
        if (blurProgram == null) {
            return;
        }
        nearPing.resize(Math.max(1, width >> 1), Math.max(1, height >> 1));
        nearPong.resize(Math.max(1, width >> 1), Math.max(1, height >> 1));
        farPing.resize(Math.max(1, width >> 2), Math.max(1, height >> 2));
        farPong.resize(Math.max(1, width >> 2), Math.max(1, height >> 2));
    }

    /**
     * 处理特效层并返回泛光纹理（0.5x 尺寸，线性过滤）。source 为 fxFBO CA0 纹理。
     */
    public int render(int sourceTex) {
        // 直通降采样到近景（0.5x，线性过滤自带轻微模糊）
        blitTexture(sourceTex, nearPing, 0f, 0f, 1f);
        // 近景 ping-pong 高斯（H+V 算一轮）
        ArcaneFBO current = nearPing;
        for (int i = 0; i < NEAR_PASSES; i++) {
            ArcaneFBO next = other(current);
            blurPass(current, next, 1f, 0f, NEAR_RADIUS);
            ArcaneFBO done = next;
            ArcaneFBO vertical = other(done);
            blurPass(done, vertical, 0f, 1f, NEAR_RADIUS);
            current = vertical;
        }
        // 近景 → 远景直通降采样（0.25x）
        blitTexture(current.colorTexture(0), farPing, 0f, 0f, 1f);
        // 远景 ping-pong 高斯（更大步进 → 更宽光晕）
        ArcaneFBO farCurrent = farPing;
        for (int i = 0; i < FAR_PASSES; i++) {
            ArcaneFBO next = other(farCurrent);
            blurPass(farCurrent, next, 1f, 0f, FAR_RADIUS);
            ArcaneFBO done = next;
            ArcaneFBO vertical = other(done);
            blurPass(done, vertical, 0f, 1f, FAR_RADIUS);
            farCurrent = vertical;
        }
        // 远景柔光加法叠回近景（glBlendFunc(ONE, ONE)）
        current.bindDraw();
        current.setDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        drawQuad(farCurrent.colorTexture(0), 0f, 0f, 1f);
        GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        return current.colorTexture(0);
    }

    private ArcaneFBO other(ArcaneFBO fbo) {
        return fbo == nearPing ? nearPong : (fbo == nearPong ? nearPing
            : (fbo == farPing ? farPong : farPing));
    }

    /** 直通拷贝（direction 0 → 中心采样，线性过滤缩小）。 */
    private void blitTexture(int sourceTex, ArcaneFBO target, float dirX, float dirY, float radius) {
        target.bindDraw();
        target.setDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        drawQuad(sourceTex, dirX, dirY, radius);
    }

    private void blurPass(ArcaneFBO source, ArcaneFBO target, float dirX, float dirY, float radius) {
        target.bindDraw();
        target.setDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        drawQuad(source.colorTexture(0), dirX, dirY, radius);
    }

    /** 用 blur program 画一张纹理（viewport 已由 bindDraw 设为目标 FBO 尺寸）。 */
    private void drawQuad(int sourceTex, float dirX, float dirY, float radius) {
        blurProgram.use();
        // 深度/纹理状态必须走 GlStateManager/RenderSystem（缓存一致性，见 FinalPass 同款注释）
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(sourceTex);
        blurProgram.uniform1i("inputTex", 0);
        blurProgram.uniform2f("direction", dirX, dirY);
        blurProgram.uniform1f("blurRadius", radius);
        quad.draw();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    @Override
    public void close() {
        if (blurProgram != null) {
            blurProgram.free();
            blurProgram = null;
        }
        if (nearPing != null) nearPing.close();
        if (nearPong != null) nearPong.close();
        if (farPing != null) farPing.close();
        if (farPong != null) farPong.close();
    }
}

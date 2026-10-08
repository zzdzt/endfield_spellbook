package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * 焚灭火环专用 screen-space pass。
 *
 * <p>只处理火环自己的 CA1，不触碰破晦阵/液氮炮的 CA0。扭曲后的结果再以
 * ONE/ONE 加算回 CA0，因此最终 bloom 仍只需要现有 BloomPass 一条链。</p>
 */
public final class FlameRingPass implements AutoCloseable {

    private PostShaders.Program program;
    private ArcaneFBO distorted;
    private final FullscreenQuad quad;

    public FlameRingPass(FullscreenQuad quad) {
        this.quad = quad;
    }

    public void init(int width, int height) {
        if (program != null) {
            return;
        }
        program = PostShaders.load("flame_ring_distort");
        distorted = new ArcaneFBO(1);
        distorted.create(Math.max(1, width), Math.max(1, height));
    }

    public void resize(int width, int height) {
        if (program == null) {
            return;
        }
        distorted.resize(Math.max(1, width), Math.max(1, height));
    }

    /** outer/body/coreTex = 三个独立材质附件；处理完成后加算合入 target 的 CA0。 */
    public void render(int outerTex, int bodyTex, int coreTex, ArcaneFBO target, float time, float strength) {
        // A. 三材质附件 → 独立中间纹理：只有火环会被扭曲/重着色。
        distorted.bindDraw();
        distorted.setDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        program.use();
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableBlend();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(outerTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(bodyTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(coreTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        program.uniform1i("outerTex", 0);
        program.uniform1i("bodyTex", 1);
        program.uniform1i("coreTex", 2);
        program.uniform1f("time", time * 0.075f);
        program.uniform1f("strength", strength);
        quad.draw();

        // B. 扭曲后的火环只加算到 CA0，成为统一 bloom 的输入。
        target.bindDraw();
        target.setDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
            GL11.GL_ONE, GL11.GL_ONE,
            GL11.GL_ONE, GL11.GL_ONE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(distorted.colorTexture(0));
        quad.draw();

        GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void close() {
        if (program != null) {
            program.free();
            program = null;
        }
        if (distorted != null) {
            distorted.close();
            distorted = null;
        }
    }
}

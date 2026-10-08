package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * GL 状态快照：后处理管线入口 save()、出口 restore()。
 *
 * 后处理大量使用原生 GL（FBO 绑定/viewport/混合/深度），若不完整恢复，
 * 之后的 vanilla GUI/世界渲染会出现拖影、深度错乱等状态泄漏。
 * 可用 GlStateManager 封装的恢复项一律走封装（同步 vanilla 状态缓存），
 * 其余（FBO 绑定、纹理单元、blendEquation）用原生 GL。
 */
public final class GlStateSnapshot {

    private static final int TRACKED_TEXTURE_UNITS = 4;

    // framebuffer
    private int drawFbo;
    private int readFbo;
    // viewport
    private final int[] viewport = new int[4];
    // vertex array
    private int vao;
    private boolean attrib0;
    // program
    private int program;
    // depth
    private boolean depthTest;
    private int depthFunc;
    private boolean depthMask;
    // blend
    private boolean blend;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEqRgb;
    private int blendEqAlpha;
    // cull / color mask / scissor
    private boolean cull;
    private final boolean[] colorMask = new boolean[4];
    private boolean scissor;
    private final int[] scissorBox = new int[4];
    // textures
    private int activeTexture;
    private final int[] textureBindings = new int[TRACKED_TEXTURE_UNITS];

    public void save() {
        drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        attrib0 = GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0;
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        depthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        blend = GL11.glGetBoolean(GL11.GL_BLEND);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        // ⚠️ 必须查询 pname 对应的当前值（0x8006~0x800B 的方程枚举），
        // 存 pname 本身（GL_BLEND_EQUATION_RGB = 0x8009，不是合法方程）会导致
        // restore 时 glBlendEquationSeparate 每帧报 GL_INVALID_ENUM。
        blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        blendEqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        cull = GL11.glGetBoolean(GL11.GL_CULL_FACE);
        ByteBuffer maskBuf = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBuf);
        for (int i = 0; i < 4; i++) {
            colorMask[i] = maskBuf.get(i) != 0;
        }
        scissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);

        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < TRACKED_TEXTURE_UNITS; i++) {
            // ⚠️ 必须走 RenderSystem.activeTexture（同步 GlStateManager 的活动单元追踪器）：
            // 裸 glActiveTexture 会让 restore 里 _bindTexture 比对错误缓存槽位 → 真绑定被跳过/错位
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            textureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(activeTexture);
    }

    public void restore() {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        // 裸 _glBindVertexArray 不经 vanilla 缓存：invalidate 使 BufferUploader
        // 下次绘制强制重绑 VAO，否则缓存命中的手/盔甲批次会以错误 VAO 绘制
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vao);
        if (attrib0) {
            GL20.glEnableVertexAttribArray(0);
        } else {
            GL20.glDisableVertexAttribArray(0);
        }
        GlStateManager._glUseProgram(program);
        if (depthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(depthMask);

        if (blend) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL20.glBlendEquationSeparate(blendEqRgb, blendEqAlpha);

        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
        GlStateManager._colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);

        if (scissor) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        } else {
            GlStateManager._disableScissorTest();
        }

        for (int i = 0; i < TRACKED_TEXTURE_UNITS; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textureBindings[i]);
        }
        RenderSystem.activeTexture(activeTexture);
    }
}

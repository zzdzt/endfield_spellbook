package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 自研帧缓冲：N 个 RGBA8 颜色附件 + 可选深度纹理附件（原生 GL，脱离 vanilla RenderTarget）。
 *
 * 与 vanilla 主 RenderTarget 的交互只有两种：
 *   1. blit 拷贝（颜色 / 深度）—— 要求深度附件与源完全同内部格式；
 *   2. 特效 RenderType 输出（OutputStateShard 里绑定本 FBO）。
 *
 * 深度纹理格式由 DepthCopier 按主 RT 实际格式查询后指定（多数环境为
 * GL_DEPTH_COMPONENT24；光影包可能注入 DEPTH24_STENCIL8 / DEPTH32F_STENCIL8）。
 */
public final class ArcaneFBO implements AutoCloseable {

    /** 深度附件描述：内部格式 + 基础格式 + 数据类型 + 附件挂点。 */
    public record DepthSpec(int internalFormat, int baseFormat, int dataType, int attachmentPoint) {
        public static DepthSpec of(int internalFormat) {
            return switch (internalFormat) {
                case GL11.GL_DEPTH_COMPONENT, GL30.GL_DEPTH_COMPONENT16,
                     GL30.GL_DEPTH_COMPONENT24 ->
                    new DepthSpec(internalFormat, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT);
                case GL30.GL_DEPTH_COMPONENT32F ->
                    new DepthSpec(internalFormat, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, GL30.GL_DEPTH_ATTACHMENT);
                case GL30.GL_DEPTH24_STENCIL8 ->
                    new DepthSpec(internalFormat, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
                case GL32.GL_DEPTH32F_STENCIL8 ->
                    new DepthSpec(internalFormat, GL30.GL_DEPTH_STENCIL, GL32.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
                default ->
                    // 未知格式退化为最普遍的 D24
                    new DepthSpec(GL30.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT);
            };
        }
    }

    private int fboId = -1;
    private int width;
    private int height;
    private final int colorCount;
    private final int[] colorTextures;
    private int depthTexture = -1;
    private DepthSpec depthSpec;

    public ArcaneFBO(int colorCount) {
        this.colorCount = colorCount;
        this.colorTextures = new int[colorCount];
        Arrays.fill(this.colorTextures, -1);
    }

    /** 创建颜色附件；深度附件延迟到 {@link #ensureDepth(int)}（格式按主 RT 查询结果动态匹配）。 */
    public void create(int width, int height) {
        this.width = width;
        this.height = height;
        fboId = GL30.glGenFramebuffers();
        for (int i = 0; i < colorCount; i++) {
            colorTextures[i] = GL11.glGenTextures();
            GlStateManager._bindTexture(colorTextures[i]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);
        }
        // 挂颜色附件
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        int[] drawBuffers = new int[colorCount];
        for (int i = 0; i < colorCount; i++) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i,
                GL11.GL_TEXTURE_2D, colorTextures[i], 0);
            drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        GL20.glDrawBuffers(drawBuffers);
        if (depthSpec != null) {
            attachDepth();
        }
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("ArcaneFBO incomplete: 0x" + Integer.toHexString(status));
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** 创建/替换深度纹理附件（格式变化或首次创建时调用）。 */
    public void ensureDepth(int internalFormat) {
        DepthSpec spec = DepthSpec.of(internalFormat);
        if (depthTexture != -1 && spec.equals(depthSpec) && fboId != -1) {
            return;
        }
        depthSpec = spec;
        if (depthTexture != -1) {
            GL11.glDeleteTextures(depthTexture);
        }
        depthTexture = GL11.glGenTextures();
        GlStateManager._bindTexture(depthTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, spec.internalFormat(), width, height, 0,
            spec.baseFormat(), spec.dataType(), (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        if (fboId != -1) {
            attachDepth();
        }
    }

    private void attachDepth() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthSpec.attachmentPoint(),
            GL11.GL_TEXTURE_2D, depthTexture, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** 绑定为绘制目标并设置 viewport（不 clear，清理由调用方显式进行）。 */
    public void bindDraw() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        // viewport 走 GlStateManager（有缓存），裸 glViewport 会失步
        GlStateManager._viewport(0, 0, width, height);
    }

    public void setDrawBuffers(int... attachments) {
        GL20.glDrawBuffers(attachments);
    }

    /** 清空指定颜色附件为透明黑（alpha 必须清零：合成时 fx.a 用于背景混合）。 */
    public void clearColorAttachments() {
        GL11.glClearColor(0f, 0f, 0f, 0f);
        // glClear 只作用于 glDrawBuffers 绑定的附件 —— 逐个切换清空最可靠，
        // 结束后恢复全部附件绑定（特效输出需要双附件同写）。
        for (int i = 0; i < colorCount; i++) {
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0 + i});
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        int[] all = new int[colorCount];
        for (int i = 0; i < colorCount; i++) {
            all[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        GL20.glDrawBuffers(all);
    }

    /** 从主 RenderTarget blit 拷贝颜色（要求两侧格式一致，RGBA8↔RGBA8 无条件兼容）。 */
    public void blitColorFrom(RenderTarget source) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL30.glBlitFramebuffer(0, 0, source.width, source.height,
            0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
    }

    /** 从主 RenderTarget blit 拷贝深度（调用方保证格式兼容，失败返回 false 不做任何事）。 */
    public boolean blitDepthFrom(RenderTarget source) {
        if (depthTexture == -1 || depthSpec == null) {
            return false;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboId);
        GL30.glBlitFramebuffer(0, 0, source.width, source.height,
            0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        int err = GL11.glGetError();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        if (err != GL11.GL_NO_ERROR) {
            return false;
        }
        return true;
    }

    public int colorTexture(int attachment) {
        return colorTextures[attachment];
    }

    /** 供 OutputStateShard 的 setup 每次求值（resize 后 fboId 会变化）。 */
    public int frameBufferId() {
        return fboId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depthInternalFormat() {
        return depthSpec == null ? -1 : depthSpec.internalFormat();
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0 || (newWidth == width && newHeight == height && fboId != -1)) {
            return;
        }
        close();
        create(newWidth, newHeight);
        if (depthSpec != null) {
            int fmt = depthSpec.internalFormat();
            depthSpec = null;
            depthTexture = -1;
            ensureDepth(fmt);
        }
    }

    @Override
    public void close() {
        if (fboId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        for (int i = 0; i < colorCount; i++) {
            if (colorTextures[i] != -1) {
                GL11.glDeleteTextures(colorTextures[i]);
                colorTextures[i] = -1;
            }
        }
        if (depthTexture != -1) {
            GL11.glDeleteTextures(depthTexture);
            depthTexture = -1;
        }
    }
}

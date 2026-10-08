package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
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
        // 防御卫语句（P0.3）：未分配/已删除的深度纹理或缺 FBO 时不得挂接，
        // 否则 glFramebufferTexture2D 收到 -1 句柄会产生 GL 错误且污染错误队列
        if (fboId == -1 || depthTexture == -1 || depthSpec == null) {
            return;
        }
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
        // P0.4：blit 前清空历史污染（错误队列是 FIFO，drain 到 GL_NO_ERROR 为止），
        // 使 blit 后的 glGetError 只反映本次操作——此前 resize 残留的错误曾把
        // depthReady 永久打假。注意此检查不能定位错误来源，只保证判定纯净。
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
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
        // 先捕获旧深度格式：close() 会删深度纹理且本方法随后把 depthSpec 归零，
        // 否则 create() 会带着 depthSpec != null 走 attachDepth()，用 -1 句柄挂接 → GL_INVALID_VALUE
        DepthSpec previousDepth = depthSpec;

        close();
        depthSpec = null; // 颜色 FBO 重建期间不得挂接已删除的深度纹理
        create(newWidth, newHeight);

        // 颜色附件就绪后再重建深度附件；深度是可选增强，失败走可降级校验，绝不抛出
        if (previousDepth != null) {
            ensureDepth(previousDepth.internalFormat());
            validateDepthAttachment();
        }
    }

    /**
     * 深度挂接后的可降级完整性校验（P0.2）：不完整时解挂并清理失败纹理，<b>绝不抛出异常</b>——
     * resize/doPost 外层的 catch 会把异常升级为 PipelinePost.failed，杀死整条管线。
     *
     * @return true = 深度附件可用；false = 已降级为无深度（颜色路径继续，后续帧/resize 可重试重建）
     */
    private boolean validateDepthAttachment() {
        if (fboId == -1) {
            return false;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return true;
        }
        EndfieldSpellbook.LOGGER.error(
            "[EndfieldSpellbook] ArcaneFBO incomplete after depth attach (0x{}) — detaching depth, degrading to color-only",
            Integer.toHexString(status));
        // 用当前 DepthSpec 实际对应的附件点解挂（D24/DEPTH_COMPONENT → GL_DEPTH_ATTACHMENT，
        // D24S8/DEPTH32F_STENCIL8 → GL_DEPTH_STENCIL_ATTACHMENT），不盲目假设只有 GL_DEPTH_ATTACHMENT
        if (depthSpec != null) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthSpec.attachmentPoint(),
                GL11.GL_TEXTURE_2D, 0, 0);
        }
        if (depthTexture != -1) {
            GL11.glDeleteTextures(depthTexture);
            depthTexture = -1;
        }
        // 解挂后复核颜色核心功能；仍不完整属于核心故障，但同样不由本可降级方法抛异常，
        // 交给后续使用该 FBO 的既有失败路径（create/blit 调用方）暴露
        status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            EndfieldSpellbook.LOGGER.error(
                "[EndfieldSpellbook] ArcaneFBO STILL incomplete without depth (0x{}) — color path broken",
                Integer.toHexString(status));
        }
        return false;
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

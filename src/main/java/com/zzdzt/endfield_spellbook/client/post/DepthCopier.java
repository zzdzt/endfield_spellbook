package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 主 RenderTarget 深度格式探测 + 深度 blit 兼容性判断。
 *
 * vanilla 1.20.1 主 RT 的深度附件是 renderbuffer（glRenderbufferStorage 用
 * GL_DEPTH_COMPONENT 占位，驱动实际规格化为 D24/D32F 等具体格式）；光影包
 * 可能注入 depth-stencil。blit GL_DEPTH_BUFFER_BIT 要求两侧深度内部格式
 * 完全一致 —— 因此先探测格式，再让 fxFBO 按相同格式建深度纹理。
 *
 * 格式查询结果按 renderbuffer/纹理 id 缓存（主 RT 重建时 id 变化，缓存自动失效）。
 */
public final class DepthCopier {

    private static long cachedSourceId = -1;
    private static int cachedFormat = -1;

    private DepthCopier() {
    }

    /**
     * 探测主 RT 深度附件的内部格式；失败返回 -1（此时管线走无深度降级）。
     */
    public static int querySourceDepthFormat(RenderTarget mainRT) {
        if (!mainRT.useDepth) {
            return -1;
        }
        long sourceId = mainRT.getDepthTextureId();
        if (sourceId <= 0) {
            return -1;
        }
        if (sourceId == cachedSourceId) {
            return cachedFormat;
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainRT.frameBufferId);
        int format = -1;
        try {
            // ⚠️ 查询 target 必须用 GL_READ_FRAMEBUFFER：上面只绑定了 READ 目标，
            // 而 GL_FRAMEBUFFER 语义 = DRAW 目标（此处仍是默认帧缓冲）→
            // GL_INVALID_ENUM "<attachment> ... default framebuffer"，深度格式探测
            // 恒失败 → depthReady=false → 整条管线降级为无深度渲染（特效穿墙）。
            int objType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int objName;
            if (objType == GL11.GL_NONE) {
                // 光影包可能把深度挂在 DEPTH_STENCIL 附件点
                objType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                objName = objType == GL11.GL_NONE ? -1 : GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            } else {
                objName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            }

            if (objType == GL30.GL_RENDERBUFFER && objName > 0) {
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, objName);
                format = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,
                    GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            } else if (objType == GL11.GL_TEXTURE && objName > 0) {
                GlStateManager._bindTexture(objName);
                format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0,
                    GL11.GL_TEXTURE_INTERNAL_FORMAT);
                GlStateManager._bindTexture(0);
            }
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        }

        if (format > 0) {
            cachedSourceId = sourceId;
            cachedFormat = format;
        }
        return format;
    }

    /** 清空缓存（主 RT 重建后由 PipelinePost 调用，防御性接口）。 */
    public static void invalidate() {
        cachedSourceId = -1;
        cachedFormat = -1;
    }
}

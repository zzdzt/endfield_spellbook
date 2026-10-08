package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 全屏四边形（自管 VAO/VBO）。
 *
 * TRIANGLE_STRIP ×4 顶点，位置 (±1, ±1)，vsh 内换算 uv = pos * 0.5 + 0.5。
 * 用 glDrawArrays 直接绘制，不经过 vanilla BufferBuilder/BufferUploader，
 * 避免干扰原版批次状态。
 */
public final class FullscreenQuad implements AutoCloseable {

    private static final float[] POSITIONS = {
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f,
    };

    private int vao;
    private int vbo;

    public void init() {
        if (vao != 0) {
            return;
        }
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GlStateManager._glBindVertexArray(vao);
        // ⚠️ ARRAY_BUFFER 走 GlStateManager（有缓存）：裸 glBindBuffer 不更新缓存，
        // 会让之后 vanilla 的 _glBindBuffer 命中脏缓存跳过真绑定 → 上传/绘制打到 0 号缓冲
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, POSITIONS, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        VertexBuffer.unbind();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    /** 绘制全屏四边形（调用方负责先 use program 与绑纹理）。 */
    public void draw() {
        // ⚠️ 原生 glBindVertexArray 会让 BufferUploader.lastImmediateBuffer 缓存脱节：
        // 该缓存命中的 vanilla 绘制（手/手持物品/盔甲等 NEW_ENTITY 格式）会跳过重绑 VAO，
        // 而真实 VAO 已被我们切走 → 以 VAO 0 绘制 → GL_INVALID_OPERATION "Array object
        // is not active" → 手部与手持物品整体不可见（1.20.1 GlStateManager 无 VAO 缓存，
        // 真正的缓存在 BufferUploader，必须用 invalidate()/VertexBuffer.unbind() 同步）。
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(0);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL20.glDisableVertexAttribArray(0);
        VertexBuffer.unbind();
    }

    public boolean ready() {
        return vao != 0;
    }

    @Override
    public void close() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(vbo);
            vao = 0;
            vbo = 0;
        }
    }
}

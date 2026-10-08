package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 后处理 GLSL 着色器编译器（原生 GL，不走 vanilla ShaderInstance 体系）。
 *
 * 为什么不用 RegisterShadersEvent：后处理 pass 是全屏三角形/四边形，不需要
 * vanilla 的顶点格式与 uniform 注入；自编译一个极简 program 更轻，也避免了
 * core shader json 与原版管线的耦合。源码从 assets/endfield_spellbook/shaders/post/ 读取，
 * 与资源包体系兼容（当前仅支持默认资源包路径）。
 *
 * 编译失败抛 {@link PostShaderException}，由 PipelinePost 捕获后整体降级回退。
 */
public final class PostShaders {

    private PostShaders() {
    }

    /** 编译/链接失败。 */
    public static class PostShaderException extends RuntimeException {
        public PostShaderException(String msg) {
            super(msg);
        }
    }

    /** 一个已链接的 GLSL program + uniform 位置缓存。 */
    public static final class Program {
        private final int id;
        private final Map<String, Integer> uniformCache = new HashMap<>();

        private Program(int id) {
            this.id = id;
        }

        public void use() {
            GlStateManager._glUseProgram(id);
        }

        public void free() {
            GL20.glDeleteProgram(id);
        }

        public int uniform(String name) {
            return uniformCache.computeIfAbsent(name,
                n -> GL20.glGetUniformLocation(id, n));
        }

        public void uniform1f(String name, float v) {
            GL20.glUniform1f(uniform(name), v);
        }

        public void uniform2f(String name, float x, float y) {
            GL20.glUniform2f(uniform(name), x, y);
        }

        public void uniform1i(String name, int v) {
            GL20.glUniform1i(uniform(name), v);
        }
    }

    /**
     * 从 assets 读取 vsh/fsh 并链接为 program。
     *
     * @param name 如 "final_composite" → endfield_spellbook:shaders/post/final_composite.vsh/.fsh
     */
    public static Program load(String name) {
        int vs = compile(readSource(name + ".vsh"), GL20.GL_VERTEX_SHADER, name + ".vsh");
        int fs = compile(readSource(name + ".fsh"), GL20.GL_FRAGMENT_SHADER, name + ".fsh");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new PostShaderException("Link failed for '" + name + "': " + log);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return new Program(program);
    }

    private static int compile(String source, int type, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new PostShaderException("Compile failed for '" + label + "': " + log);
        }
        return shader;
    }

    private static String readSource(String fileName) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
            EndfieldSpellbook.MOD_ID, "shaders/post/" + fileName);
        try {
            InputStream in = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(rl).open();
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new PostShaderException("Missing shader resource " + rl + ": " + e);
        }
    }
}

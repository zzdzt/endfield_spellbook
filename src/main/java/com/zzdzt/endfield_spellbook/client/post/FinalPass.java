package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;

/**
 * 最终合成 pass：把 特效层(fx) + 泛光(bloom) 合成到主 RenderTarget。
 *
 * 采样三张纹理（⚠️ 场景必须传 sceneFBO 拷贝而非主 RT 自身纹理 —— 直接采样
 * 绘制目标属于未定义行为 / feedback loop）：
 *   unit0 sceneTex  : 主画面拷贝（sceneFBO CA0）
 *   unit1 fxTex     : 特效层（fxFBO CA0，加算累积，alpha 线性累加）
 *   unit2 bloomTex  : 泛光结果（bloom ping-pong 输出，未启用时传黑色纹理）
 *
 * 合成：out.rgb = fx.rgb + scene.rgb * (1 - fx.a) + bloom.rgb * strength
 * fx.rgb 为加算叠加项；(1-fx.a) 提供少量压暗（能量场的"膜"感）；alpha 取 scene。
 */
public final class FinalPass implements AutoCloseable {

    private PostShaders.Program program;

    public void init() {
        if (program != null) {
            return;
        }
        program = PostShaders.load("final_composite");
    }

    /**
     * 绘制到当前绑定的 framebuffer（调用方先 bindWrite 主 RT）。
     * 内部关闭深度测试与混合（合成是全屏覆盖，混合逻辑在 shader 内完成）。
     */
    public void render(FullscreenQuad quad, int sceneTex, int fxTex, int bloomTex,
                       float bloomStrength, float fxBrightness) {
        program.use();
        // 深度/混合状态必须走 GlStateManager（有缓存），裸 GL 会失步导致后续 RenderType setup 被跳过
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableBlend();

        // ⚠️ 纹理状态必须走 RenderSystem.activeTexture + GlStateManager._bindTexture：
        // 裸 glActiveTexture/glBindTexture 不更新 GlStateManager 的逐单元绑定缓存，
        // 管线结束后手持 pass 的 _bindTexture(皮肤) 命中脏缓存被跳过 → 手臂采样陈旧纹理 → 透明消失
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(sceneTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(fxTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(bloomTex);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        program.uniform1i("sceneTex", 0);
        program.uniform1i("fxTex", 1);
        program.uniform1i("bloomTex", 2);
        program.uniform1f("bloomStrength", bloomStrength);
        program.uniform1f("fxBrightness", fxBrightness);

        quad.draw();
    }

    @Override
    public void close() {
        if (program != null) {
            program.free();
            program = null;
        }
    }
}

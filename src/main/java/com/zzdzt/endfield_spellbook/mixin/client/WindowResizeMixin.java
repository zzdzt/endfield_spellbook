package com.zzdzt.endfield_spellbook.mixin.client;

import com.zzdzt.endfield_spellbook.client.post.PipelinePost;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 窗口 framebuffer 尺寸变化 → 重建后处理管线的 FBO。
 * 注入 RETURN 点：vanilla 已完成主 RenderTarget resize 之后再重建我们的。
 */
@Mixin(Window.class)
public abstract class WindowResizeMixin {

    @Inject(method = "onFramebufferResize", at = @At("RETURN"))
    private void endfieldspellbook$onFramebufferResize(long window, int width, int height, CallbackInfo ci) {
        PipelinePost.INSTANCE.onFramebufferResize(width, height);
    }
}

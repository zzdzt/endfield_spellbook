package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.config.ClientConfig;
import com.zzdzt.endfield_spellbook.config.VfxQuality;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 特效自研后处理管线（破晦阵 / 液氮炮 / 焚灭火环等统一调度）。
 *
 * 特效接入协议（技术债泛化后）：实现 {@link PostFxSnapshot}（entity + drawInto +
 * endBatches + 可选 screen-space 钩子），实体 pass 调 {@link #enqueue}——管线零修改。
 *
 * 每帧流程（仅当本帧有特效快照时执行，队列为空零开销）：
 *   1. GlStateSnapshot.save()
 *   2. blit 主 RT 颜色 → sceneFBO（final 合成的采样源，避免采样主 RT 自身）
 *   3. blit 主 RT 深度 → fxFBO（格式动态匹配；失败走无深度降级）
 *   4. 清 fxFBO → 按快照重绘特效（PostRenderTypes 写入 CA0/CA1）
 *   5. BloomPass：CA0 → 双级 ping-pong 高斯 → 泛光纹理
 *   6. 主 RT bindWrite → FinalPass 全屏合成（scene*(1-fx.a) + fx + bloom*strength）
 *   7. GlStateSnapshot.restore()
 *
 * 降级链：配置关闭 / 初始化失败（shader 编译、FBO 不完整等）/ 无主 RT
 * → isActive()=false → 各实体渲染器走原有直绘路径（PARTICLES_TARGET）。
 */
@Mod.EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID, value = Dist.CLIENT)
public final class PipelinePost {

    public static final PipelinePost INSTANCE = new PipelinePost();

    private boolean initialized;
    private boolean failed;
    private boolean depthReady;
    private boolean loggedActive;

    private final GlStateSnapshot snapshot = new GlStateSnapshot();
    private final FullscreenQuad quad = new FullscreenQuad();
    private ArcaneFBO sceneFBO;
    private ArcaneFBO fxFBO;
    private FinalPass finalPass;
    private BloomPass bloomPass;
    private FlameRingPass flameRingPass;
    /** 火环专属 Pass 故障标志（P1 隔离）：只禁用火环后处理，不影响公共管线。 */
    private boolean flameRingPassFailed;

    private final List<PostFxSnapshot> snapshots = new ArrayList<>(8);

    private PipelinePost() {
    }

    // ===== 查询 =====

    /** 管线是否处于可用状态（配置 + 初始化 + 无致命失败）。 */
    public static boolean isActive() {
        if (!INSTANCE.loggedActive && configEnabled() && !INSTANCE.failed) {
            INSTANCE.loggedActive = true;
            EndfieldSpellbook.LOGGER.info("[EndfieldSpellbook] Post pipeline enabled (gloompurge + lnc + flame ring)");
        }
        return configEnabled() && !INSTANCE.failed;
    }

    private static boolean configEnabled() {
        try {
            return ClientConfig.POST_PIPELINE.get();
        } catch (IllegalStateException e) {
            // 配置尚未加载（极早期渲染调用）
            return false;
        }
    }

    private static float bloomStrength() {
        return (float) (double) ClientConfig.POST_BLOOM_STRENGTH.get();
    }

    private static float fxBrightness() {
        return (float) (double) ClientConfig.POST_BRIGHTNESS.get();
    }

    private static boolean bloomEnabled() {
        try {
            return ClientConfig.VFX_QUALITY.get() != VfxQuality.LOW;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** PostRenderTypes 的 OutputStateShard setup 取当前 fxFBO。 */
    public static ArcaneFBO fxBuffer() {
        return INSTANCE.fxFBO;
    }

    // ===== 快照收集 =====

    /** 实体 pass 冻结渲染参数（任意 PostFxSnapshot 实现；AFTER_LEVEL 消费后清空）。 */
    public static void enqueue(PostFxSnapshot snapshot) {
        INSTANCE.snapshots.add(snapshot);
    }

    /** 火环 screen-space 扭曲段的访问入口（FlameRingSnapshot 钩子用）。 */
    static FlameRingPass flameRingPass() {
        return INSTANCE.flameRingPass;
    }

    /**
     * 火环 screen-space Pass 是否真实可用（P1.2）：管线启用 + 初始化成功 + 未运行期失效。
     * 对象存在不代表可用，必须查故障标志。
     */
    public static boolean flameRingPassUsable() {
        return isActive() && !INSTANCE.flameRingPassFailed && INSTANCE.flameRingPass != null;
    }

    // ===== 帧流程 =====

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        INSTANCE.frame(event);
    }

    private void frame(RenderLevelStageEvent event) {
        // 无特效快照 → 管线整体跳过（恒等合成无意义），零开销
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            ensureInitialized();
            if (failed) {
                return;
            }
            doPost(event);
        } catch (Throwable t) {
            failed = true;
            EndfieldSpellbook.LOGGER.error("[EndfieldSpellbook] Post pipeline disabled after failure", t);
        } finally {
            snapshots.clear();
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        if (rt == null || rt.width <= 0 || rt.height <= 0) {
            throw new IllegalStateException("Main render target unavailable");
        }
        sceneFBO = new ArcaneFBO(1);
        sceneFBO.create(rt.width, rt.height);
        fxFBO = new ArcaneFBO(4);
        fxFBO.create(rt.width, rt.height);
        quad.init();
        finalPass = new FinalPass();
        finalPass.init();
        bloomPass = new BloomPass(quad);
        bloomPass.init(rt.width, rt.height);
        initFlameRingPass(rt.width, rt.height);
        syncDepthFormat();
        initialized = true;
        EndfieldSpellbook.LOGGER.info("[EndfieldSpellbook] Post pipeline initialized: {}x{}, depthFormat=0x{}",
            rt.width, rt.height, Integer.toHexString(fxFBO.depthInternalFormat()));
    }

    /**
     * 火环专属 Pass 独立故障边界（P1.1）：Shader 加载/编译/链接失败只禁用火环后处理
     * （快照自动切换 CA0 fallback），绝不把公共管线标记为 failed。
     */
    private void initFlameRingPass(int width, int height) {
        if (flameRingPassFailed) {
            return;
        }
        try {
            flameRingPass = new FlameRingPass(quad);
            flameRingPass.init(width, height);
        } catch (Throwable t) {
            if (flameRingPass != null) {
                flameRingPass.close(); // 释放半初始化资源（program/distorted）
                flameRingPass = null;
            }
            flameRingPassFailed = true;
            EndfieldSpellbook.LOGGER.error(
                "[EndfieldSpellbook] FlameRingPass init failed — ring degrades to CA0 fallback, other effects unaffected", t);
        }
    }

    /** 深度格式与主 RT 同步（主 RT 重建/光影切换时格式可能变化）。 */
    private void syncDepthFormat() {
        Minecraft mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        int fmt = DepthCopier.querySourceDepthFormat(rt);
        if (fmt <= 0) {
            depthReady = false;
            return;
        }
        fxFBO.ensureDepth(fmt);
        depthReady = true;
    }

    /** 窗口 resize（WindowResizeMixin 调用）。 */
    public static void onFramebufferResize(int width, int height) {
        PipelinePost self = INSTANCE;
        if (!self.initialized || self.failed || width <= 0 || height <= 0) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            var rt = mc.getMainRenderTarget();
            self.sceneFBO.resize(rt.width, rt.height);
            self.fxFBO.resize(rt.width, rt.height);
            self.bloomPass.resize(rt.width, rt.height);
            if (self.flameRingPass != null && !self.flameRingPassFailed) {
                self.flameRingPass.resize(rt.width, rt.height);
            }
            DepthCopier.invalidate();
            self.syncDepthFormat();
        } catch (Throwable t) {
            self.failed = true;
            EndfieldSpellbook.LOGGER.error("[EndfieldSpellbook] Post pipeline resize failed, disabling", t);
        }
    }

    private void doPost(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        // 主 RT 尺寸异常防御（罕见的外部重建）
        if (fxFBO.width() != rt.width || fxFBO.height() != rt.height) {
            onFramebufferResize(rt.width, rt.height);
            if (failed) {
                return;
            }
        }

        snapshot.save();
        try {
            // ① 拷贝主画面颜色（final 合成的采样源）
            sceneFBO.blitColorFrom(rt);

            // ② fxFBO：清屏 + 同步真实场景深度
            fxFBO.bindDraw();
            fxFBO.clearColorAttachments();
            if (depthReady) {
                depthReady = fxFBO.blitDepthFrom(rt);
            }

            // ③ 按快照重绘特效到 fxFBO（frame() 已保证队列非空）
            drawSnapshots(event, mc);

            // ④ 快照自报的 screen-space 附加段（如火环三材质 CA 扭曲并回 CA0）：
            //    每帧只执行第一个声明者的，与重构前 get(0) 取时语义一致。
            //    运行期异常在本地隔离（P1.4）：标记 Pass 失效（后续帧自动切 CA0 fallback），
            //    绝不冒泡到全局 failed；GL 状态残留由 finally 的 GlStateSnapshot.restore() 兜底，
            //    Bloom/FinalPass 各自重新绑定目标，不在污染状态下执行。
            for (PostFxSnapshot s : snapshots) {
                if (s.hasScreenSpacePass()) {
                    if (flameRingPassUsable()) {
                        try {
                            s.runScreenSpacePass();
                        } catch (Throwable t) {
                            flameRingPassFailed = true;
                            EndfieldSpellbook.LOGGER.error(
                                "[EndfieldSpellbook] FlameRing screen-space pass failed at runtime — disabled, ring falls back to CA0", t);
                        }
                    }
                    break;
                }
            }

            // ⑤ bloom
            int bloomTex = fxFBO.colorTexture(1);
            float strength = 0f;
            if (bloomEnabled() && bloomStrength() > 0f) {
                bloomTex = bloomPass.render(fxFBO.colorTexture(0));
                strength = bloomStrength();
            }

            // ⑥ 合成回主 RT（fxBrightness 为防过曝主旋钮，作用于 fx 与 bloom）
            rt.bindWrite(true);
            finalPass.render(quad, sceneFBO.colorTexture(0), fxFBO.colorTexture(0), bloomTex,
                strength, fxBrightness());
        } finally {
            snapshot.restore();
        }
    }

    private void drawSnapshots(RenderLevelStageEvent event, Minecraft mc) {
        // ⚠️ AFTER_LEVEL 事件的 getPoseStack() = 投影+bob 矩阵（GameRenderer.renderLevel
        // line 1128 传的是局部 posestack），不含相机旋转——直接用会把特效"透视化"钉在屏幕上。
        // 这里自建相机旋转（复刻 GameRenderer.renderLevel line 1121-1122 的矩阵顺序），
        // 此时 GL ModelViewMat 已被 vanilla 恢复为 identity，顶点 = camRot × (entity-cam) × local。
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 统一重绘：translate 由管线完成，绘制与批次归属由各快照自报
        for (PostFxSnapshot s : snapshots) {
            poseStack.pushPose();
            poseStack.translate(
                s.entity().getX() - camPos.x,
                s.entity().getY() - camPos.y,
                s.entity().getZ() - camPos.z);
            s.drawInto(poseStack, buffers, depthReady);
            poseStack.popPose();
        }

        // 批次收尾：批次跨同类型快照共享，每种实现类型每帧只 endBatch 一次
        Set<Class<?>> settled = new HashSet<>();
        for (PostFxSnapshot s : snapshots) {
            if (settled.add(s.getClass())) {
                s.endBatches(buffers, depthReady);
            }
        }
    }
}

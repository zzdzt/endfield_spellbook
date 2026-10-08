package com.zzdzt.endfield_spellbook.client.post;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.config.ClientConfig;
import com.zzdzt.endfield_spellbook.config.VfxQuality;
import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkRenderer;
import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 特效自研后处理管线（破晦阵）。
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

    private final List<MarkSnapshot> snapshots = new ArrayList<>(8);
    private final List<LncSnapshot> lncSnapshots = new ArrayList<>(8);

    private PipelinePost() {
    }

    // ===== 查询 =====

    /** 管线是否处于可用状态（配置 + 初始化 + 无致命失败）。 */
    public static boolean isActive() {
        if (!INSTANCE.loggedActive && configEnabled() && !INSTANCE.failed) {
            INSTANCE.loggedActive = true;
            EndfieldSpellbook.LOGGER.info("[EndfieldSpellbook] Post pipeline enabled (gloompurge)");
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

    /** 破晦阵实体 pass 冻结渲染参数（每帧末尾在 AFTER_LEVEL 消费后清空）。 */
    public static void enqueue(MarkSnapshot snapshot) {
        INSTANCE.snapshots.add(snapshot);
    }

    /** 液氮炮弹实体 pass 冻结渲染参数（与破晦阵共用一条管线，消费后清空）。 */
    public static void enqueue(LncSnapshot snapshot) {
        INSTANCE.lncSnapshots.add(snapshot);
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
        if (snapshots.isEmpty() && lncSnapshots.isEmpty()) {
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
            lncSnapshots.clear();
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
        fxFBO = new ArcaneFBO(2);
        fxFBO.create(rt.width, rt.height);
        quad.init();
        finalPass = new FinalPass();
        finalPass.init();
        bloomPass = new BloomPass(quad);
        bloomPass.init(rt.width, rt.height);
        syncDepthFormat();
        initialized = true;
        EndfieldSpellbook.LOGGER.info("[EndfieldSpellbook] Post pipeline initialized: {}x{}, depthFormat=0x{}",
            rt.width, rt.height, Integer.toHexString(fxFBO.depthInternalFormat()));
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

            // ④ bloom
            int bloomTex = fxFBO.colorTexture(1);
            float strength = 0f;
            if (bloomEnabled() && bloomStrength() > 0f) {
                bloomTex = bloomPass.render(fxFBO.colorTexture(0));
                strength = bloomStrength();
            }

            // ⑤ 合成回主 RT（fxBrightness 为防过曝主旋钮，作用于 fx 与 bloom）
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

        // 破晦阵（纯加算）：逐类型 endBatch 确保每个批次的 setup（绑 fxFBO）→
        // 绘制 → teardown（绑回主RT）完整执行
        var set = PostRenderTypes.set(depthReady);
        for (MarkSnapshot s : snapshots) {
            poseStack.pushPose();
            poseStack.translate(
                s.entity().getX() - camPos.x,
                s.entity().getY() - camPos.y,
                s.entity().getZ() - camPos.z);
            // renderer 实例承载 fadeTick 等实例状态，从 dispatcher 反查
            var renderer = mc.getEntityRenderDispatcher().getRenderer(s.entity());
            if (renderer instanceof GloompurgeMarkRenderer gmr) {
                gmr.drawForPipeline(s.entity(), s.f(), s.alpha(), s.dist(),
                    s.shape(), poseStack, buffers, set);
            }
            poseStack.popPose();
        }
        buffers.endBatch(set.mark());
        buffers.endBatch(set.ground());
        buffers.endBatch(set.mist());
        buffers.endBatch(set.glow());

        // 液氮炮弹（纯加算）：球体/拖尾/命中环走 glow 批次
        if (!lncSnapshots.isEmpty()) {
            var lncSet = PostRenderTypes.set(depthReady);
            for (LncSnapshot s : lncSnapshots) {
                poseStack.pushPose();
                poseStack.translate(
                    s.entity().getX() - camPos.x,
                    s.entity().getY() - camPos.y,
                    s.entity().getZ() - camPos.z);
                var renderer = mc.getEntityRenderDispatcher().getRenderer(s.entity());
                if (renderer instanceof LncProjectileRenderer lncRenderer) {
                    lncRenderer.drawForPipeline(s.entity(), s.f(), poseStack, buffers, lncSet.glow());
                }
                poseStack.popPose();
            }
            buffers.endBatch(lncSet.glow());
        }
    }
}

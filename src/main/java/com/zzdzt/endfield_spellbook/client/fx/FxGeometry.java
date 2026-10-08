package com.zzdzt.endfield_spellbook.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 法术特效共享几何算子库（移植自 ArcaneMag）。
 *
 * 目标：新法术特效不再手写顶点——所有线条/面片都从这里取算子组合。
 * 全部方法为纯静态、无状态、无 Minecraft 依赖（相机坐标等由调用方传入），
 * 顶点统一走 {@link #vertex}（NEW_ENTITY 格式：color/uv/overlay/fullbright+法线）。
 *
 * 线条算子选型（⚠️ 用错会静默不可见，见各类注释）：
 * - {@link #edge}          贴地/水平薄片（竖直段会退化为无宽度细线，勿用）
 * - {@link #cylEdge}       圆柱面/墙面元素（需径向法线）
 * - {@link #billboardBeam} 自由空间光束/拖尾（始终正对相机，需相机坐标）
 */
public final class FxGeometry {

    private FxGeometry() {
    }

    /** float → float 一元函数（宽度/alpha 沿 progress 的剖面曲线）。不使用 java.util.function 版本以规避个别 toolchain 的解析异常 */
    @FunctionalInterface
    public interface FloatCurve {
        float apply(float progress);
    }

    // ==================================================================
    // 线条算子
    // ==================================================================

    /**
     * 单条边：贴地薄片 quad（端点可异高异色）。
     * 宽度方向在水平面内、垂直于线段；len 只算水平投影——
     * ⚠️ 竖直线段 (dx=dz=0) 会被静默丢弃，墙面元素请用 {@link #cylEdge}。
     */
    public static void edge(PoseStack pose, VertexConsumer consumer,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float width, float alpha, float[] colA, float[] colB) {
        float dx = bx - ax;
        float dz = bz - az;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        // 水平法线（薄片宽度方向）
        float nx = -dz / len * width * 0.5f;
        float nz = dx / len * width * 0.5f;

        var mat = pose.last();
        vertex(mat, consumer, ax + nx, ay, az + nz, colA[0], colA[1], colA[2], alpha, 0.4f, 0.4f);
        vertex(mat, consumer, bx + nx, by, bz + nz, colB[0], colB[1], colB[2], alpha, 0.6f, 0.4f);
        vertex(mat, consumer, bx - nx, by, bz - nz, colB[0], colB[1], colB[2], alpha, 0.6f, 0.6f);
        vertex(mat, consumer, ax - nx, ay, az - nz, colA[0], colA[1], colA[2], alpha, 0.4f, 0.6f);
    }

    /**
     * 圆柱面上的线段薄片：宽度方向 = cross(径向法线 n, 线段方向 d)，
     * 使薄片始终「贴合」圆柱面 —— 竖直线段宽度沿切向、水平线段宽度沿竖直方向，
     * 从圆柱外侧看两者都是正面朝向，平视也清晰可见。
     *
     * ⚠️ 与 {@link #edge} 的区别（后者只适用于贴地图形）：
     * edge 的水平薄片在墙面水平走线平视时退化成无宽度的数学线；
     * 所以一切墙面元素（PCB 走线 / 焊盘 / 数据流亮点）必须走这里。
     *
     * @param nrmX  归一化径向法线 X 分量（圆柱面即 cos(theta)）
     * @param nrmZ  归一化径向法线 Z 分量（圆柱面即 sin(theta)）
     */
    public static void cylEdge(PoseStack pose, VertexConsumer consumer,
                               float ax, float ay, float az, float bx, float by, float bz,
                               float width, float alpha, float[] colA, float[] colB,
                               float nrmX, float nrmZ) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        // w = cross(n, d)，n = (nrmX, 0, nrmZ)
        float wx = -nrmZ * dy;
        float wy = nrmZ * dx - nrmX * dz;
        float wz = nrmX * dy;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1e-6f) return; // 线段与法线平行（退化，无合法宽度方向）
        float s = width * 0.5f / wl;
        wx *= s;
        wy *= s;
        wz *= s;

        var mat = pose.last();
        vertex(mat, consumer, ax + wx, ay + wy, az + wz, colA[0], colA[1], colA[2], alpha, 0.4f, 0.4f);
        vertex(mat, consumer, bx + wx, by + wy, bz + wz, colB[0], colB[1], colB[2], alpha, 0.6f, 0.4f);
        vertex(mat, consumer, bx - wx, by - wy, bz - wz, colB[0], colB[1], colB[2], alpha, 0.6f, 0.6f);
        vertex(mat, consumer, ax - wx, ay - wy, az - wz, colA[0], colA[1], colA[2], alpha, 0.4f, 0.6f);
    }

    /**
     * billboard 光束：宽度方向 = cross(线段方向 d, 视线 v)，薄片始终正对相机。
     * 自由空间的光束/拖尾/连线用这个；铁律同上：贴地图形用 edge，墙面用 cylEdge。
     * uv 沿线段长度 v: 0→1（配合 v 方向平铺纹理可得"链条感"），横向 u 固定 0.4/0.6。
     *
     * @param camX/Y/Z 相机世界坐标（Camera.getPosition()），用于视线方向
     */
    public static void billboardBeam(PoseStack pose, VertexConsumer consumer,
                                     float ax, float ay, float az, float bx, float by, float bz,
                                     float width, float alpha, float[] colA, float[] colB,
                                     float camX, float camY, float camZ) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        // 视线：相机 → 线段中点
        float vx = (ax + bx) * 0.5f - camX;
        float vy = (ay + by) * 0.5f - camY;
        float vz = (az + bz) * 0.5f - camZ;
        // w = cross(d, view)
        float wx = dy * vz - dz * vy;
        float wy = dz * vx - dx * vz;
        float wz = dx * vy - dy * vx;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1e-6f) return; // 正对/背对线段（退化），换个观测角度即可见，不做兜底
        float s = width * 0.5f / wl;
        wx *= s;
        wy *= s;
        wz *= s;

        var mat = pose.last();
        vertex(mat, consumer, ax + wx, ay + wy, az + wz, colA[0], colA[1], colA[2], alpha, 0.4f, 0f);
        vertex(mat, consumer, bx + wx, by + wy, bz + wz, colB[0], colB[1], colB[2], alpha, 0.6f, 0f);
        vertex(mat, consumer, bx - wx, by - wy, bz - wz, colB[0], colB[1], colB[2], alpha, 0.6f, 1f);
        vertex(mat, consumer, ax - wx, ay - wy, az - wz, colA[0], colA[1], colA[2], alpha, 0.4f, 1f);
    }

    /**
     * 折线 ribbon：把任意点列展开成始终正对相机的光带（拖尾/光束/闪电的底座）。
     * - 点数 ≥3 时先做一次 Chaikin 圆角（急弯不越界，无需样条）；
     * - 每点横向量 = normalize(切线 × 视线)，**用相邻点**算切线 → 拐角法线连续不断裂；
     *   相邻点 side 点积 <0 时整体取负（翻面保护）；
     * - 宽度/透明度是 progress 的函数（0=首点尾端，1=末点头端）→ 头粗尾细/渐隐；
     * - uv：u 横向 [uMin,uMax]（配全幅横向渐变纹理传 0,1），v = progress 沿长度。
     *
     * <p>视线在本方法内部解决：EntityRenderer.render 的 poseStack 已含
     * "实体−相机"平移，故相机在 pose 局部系中的位置 = normal 矩阵逆转 (−mat.position)，
     * 调用方无需传相机坐标。path 用局部（实体相对）坐标。</p>
     *
     * @param path      点列（局部坐标，≥2 点），首点=尾端、末点=头端
     * @param halfWidth progress → 半宽（格）
     * @param alphaMul  progress → alpha 倍率（× baseAlpha）
     */
    public static void polylineRibbon(PoseStack pose, VertexConsumer consumer,
                                      List<Vector3f> path,
                                      FloatCurve halfWidth, FloatCurve alphaMul,
                                      float baseAlpha, float r, float g, float b,
                                      float uMin, float uMax) {
        int raw = path.size();
        if (raw < 2) return;
        List<Vector3f> pts = raw >= 3 ? chaikinSmooth(path) : path;
        int n = pts.size();

        PoseStack.Pose mat = pose.last();
        // 相机在局部系的位置：R^T × (camPos − entityPos)，R = mat.normal()（局部→世界方向）。
        // 1.20.1 Pose 不暴露平移 getter，从 Matrix4f 的 m3x 列取
        var m4 = mat.pose();
        Vector3f camLocal = new Vector3f(-m4.m30(), -m4.m31(), -m4.m32());
        Matrix3f inv = new Matrix3f(mat.normal());
        inv.transpose().transform(camLocal);

        // 逐点：切线（相邻点差分）、视线、side 横向量、宽、alpha
        float[] halfW = new float[n];
        float[] alphas = new float[n];
        Vector3f[] sides = new Vector3f[n];
        Vector3f prevSide = null;
        for (int i = 0; i < n; i++) {
            Vector3f p = pts.get(i);
            float progress = (float) i / (n - 1);
            Vector3f t = tangent(pts, i).normalize();
            Vector3f view = new Vector3f(camLocal).sub(p);
            if (view.lengthSquared() < 1e-8f) view.set(0, 1e-4f, 0);
            view.normalize();
            Vector3f side = new Vector3f(t).cross(view);
            if (side.lengthSquared() < 1e-8f) {
                // 视线与切线共线（正对线段）：退世界上向，再退 X 轴
                side = new Vector3f(t).cross(0f, 1f, 0f);
                if (side.lengthSquared() < 1e-8f) side = new Vector3f(t).cross(1f, 0f, 0f);
            }
            side.normalize();
            if (prevSide != null && prevSide.dot(side) < 0) side.negate(); // 翻面保护
            prevSide = side;
            sides[i] = side;
            halfW[i] = halfWidth.apply(progress);
            alphas[i] = baseAlpha * alphaMul.apply(progress);
        }

        // 相邻点连 quad
        for (int i = 0; i < n - 1; i++) {
            Vector3f a = pts.get(i), bside = pts.get(i + 1);
            Vector3f sa = sides[i], sb = sides[i + 1];
            float wa = halfW[i], wb = halfW[i + 1];
            float aa = alphas[i], ab = alphas[i + 1];
            float progress = (float) i / (n - 1);
            float v0 = progress, v1 = (float) (i + 1) / (n - 1);
            vertex(mat, consumer, a.x + sa.x * wa, a.y + sa.y * wa, a.z + sa.z * wa, r, g, b, aa, uMin, v0);
            vertex(mat, consumer, bside.x + sb.x * wb, bside.y + sb.y * wb, bside.z + sb.z * wb, r, g, b, ab, uMin, v1);
            vertex(mat, consumer, bside.x - sb.x * wb, bside.y - sb.y * wb, bside.z - sb.z * wb, r, g, b, ab, uMax, v1);
            vertex(mat, consumer, a.x - sa.x * wa, a.y - sa.y * wa, a.z - sa.z * wa, r, g, b, aa, uMax, v0);
        }
    }

    /** Chaikin 圆角一次（Q=0.75A+0.25B / R=0.25A+0.75B，保留首尾端点） */
    public static List<Vector3f> chaikinSmooth(List<Vector3f> path) {
        int n = path.size();
        if (n < 3) return path;
        List<Vector3f> out = new ArrayList<>(n * 2);
        out.add(path.get(0));
        for (int i = 0; i < n - 1; i++) {
            Vector3f a = path.get(i), bpt = path.get(i + 1);
            out.add(new Vector3f(a).mul(0.75f).add(new Vector3f(bpt).mul(0.25f)));
            out.add(new Vector3f(a).mul(0.25f).add(new Vector3f(bpt).mul(0.75f)));
        }
        out.add(path.get(n - 1));
        return out;
    }

    /** 相邻点差分切线（端点用单侧差分） */
    private static Vector3f tangent(List<Vector3f> pts, int i) {
        int n = pts.size();
        Vector3f a = pts.get(Math.max(0, i - 1));
        Vector3f b = pts.get(Math.min(n - 1, i + 1));
        return new Vector3f(b).sub(a);
    }

    /**
     * 正多边形轮廓（贴地）。
     */
    public static void polygon(PoseStack pose, VertexConsumer consumer, int sides,
                               float radius, float rotDegrees, float y, float width, float alpha, float[] col) {
        if (radius <= 0.01f) return;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.toRadians(rotDegrees + i * 360f / sides);
            double a1 = Math.toRadians(rotDegrees + (i + 1) * 360f / sides);
            edge(pose, consumer,
                (float) Math.cos(a0) * radius, y, (float) Math.sin(a0) * radius,
                (float) Math.cos(a1) * radius, y, (float) Math.sin(a1) * radius,
                width, alpha, col, col);
        }
    }

    /**
     * 圆弧：分段边 quad 逼近（贴地）。
     */
    public static void arc(PoseStack pose, VertexConsumer consumer, float radius,
                           float startDeg, float endDeg, int steps, float y, float width, float alpha, float[] col) {
        for (int i = 0; i < steps; i++) {
            double a0 = Math.toRadians(startDeg + (endDeg - startDeg) * i / steps);
            double a1 = Math.toRadians(startDeg + (endDeg - startDeg) * (i + 1) / steps);
            edge(pose, consumer,
                (float) Math.cos(a0) * radius, y, (float) Math.sin(a0) * radius,
                (float) Math.cos(a1) * radius, y, (float) Math.sin(a1) * radius,
                width, alpha, col, col);
        }
    }

    /**
     * 竖直幕体 quad：底部接地、顶部可异高；颜色取纹理，u 为周向、v 为底/顶采样坐标。
     */
    public static void verticalQuad(PoseStack pose, VertexConsumer consumer,
                                    float ax, float az, float bx, float bz,
                                    float yBottom, float yTopA, float yTopB,
                                    float alphaBottom, float alphaTop,
                                    float u0, float u1, float vBottom, float vTop) {
        var mat = pose.last();
        vertex(mat, consumer, ax, yBottom, az, 1f, 1f, 1f, alphaBottom, u0, vBottom);
        vertex(mat, consumer, bx, yBottom, bz, 1f, 1f, 1f, alphaBottom, u1, vBottom);
        vertex(mat, consumer, bx, yTopB, bz, 1f, 1f, 1f, alphaTop, u1, vTop);
        vertex(mat, consumer, ax, yTopA, az, 1f, 1f, 1f, alphaTop, u0, vTop);
    }

    /**
     * {@link #verticalQuad} 的 POSITION_COLOR_TEX 精简版：只发位置/颜色/uv（顶点色恒白）。
     * ⚠️ 自定义 shader RenderType 用 POSITION_COLOR_TEX，顶点链里不能出现
     * overlay / uv2 / normal（格式不含这些元素），否则必须走这个版本。
     */
    public static void verticalQuadTex(PoseStack pose, VertexConsumer consumer,
                                       float ax, float az, float bx, float bz,
                                       float yBottom, float yTopA, float yTopB,
                                       float alphaBottom, float alphaTop,
                                       float u0, float u1, float vBottom, float vTop) {
        var mat = pose.last();
        vertexTex(mat, consumer, ax, yBottom, az, alphaBottom, u0, vBottom);
        vertexTex(mat, consumer, bx, yBottom, bz, alphaBottom, u1, vBottom);
        vertexTex(mat, consumer, bx, yTopB, bz, alphaTop, u1, vTop);
        vertexTex(mat, consumer, ax, yTopA, az, alphaTop, u0, vTop);
    }

    /**
     * 地面大 quad：贴地覆盖整个领域；四角超出圆的部分由纹理径向淡出隐藏。
     */
    public static void groundQuad(PoseStack pose, VertexConsumer consumer, float radius, float alpha) {
        groundQuad(pose, consumer, radius, 0.03f, alpha);
    }

    /** {@link #groundQuad} 指定高度版：多层贴地 quad（如内外圈平台）需错开高度避免共面。 */
    public static void groundQuad(PoseStack pose, VertexConsumer consumer, float radius, float y, float alpha) {
        var mat = pose.last();
        vertex(mat, consumer, -radius, y, -radius, 1f, 1f, 1f, alpha, 0f, 0f);
        vertex(mat, consumer, radius, y, -radius, 1f, 1f, 1f, alpha, 1f, 0f);
        vertex(mat, consumer, radius, y, radius, 1f, 1f, 1f, alpha, 1f, 1f);
        vertex(mat, consumer, -radius, y, radius, 1f, 1f, 1f, alpha, 0f, 1f);
    }

    // ==================================================================
    // 面片与体算子
    // ==================================================================

    /**
     * 正对相机的方形 quad（billboard）：中心固定在当前 pose 的局部原点，
     * 沿视线朝相机推 pushOut（沿视线 → 投影到屏幕上无位移，用于避免与背后 3D 元素 z-fight）。
     *
     * 正交基构造：
     * right = normalize(view × refUp)，up = normalize(right × view)，二者都垂直于视线
     * → quad 必然正对相机，且中心恒等于局部原点，不会随视角偏移。
     *
     * 相机位置从 pose 反解（同 {@link #polylineRibbon}），调用方无需传相机坐标。
     * 法线取视线方向：加算 RenderType 下无影响，但走正常混合/实体着色时
     * 这才是正确的受光朝向（私有 {@link #vertex} 硬编码 (0,1,0)，不能用于 billboard）。
     *
     * @param halfSize 半边长（格）
     * @param pushOut  沿视线朝相机的偏移（格），传 0 表示不偏移
     * @param col      顶点色 RGB（0~1）；颜色由纹理驱动时传 {1,1,1}
     */
    public static void billboardQuad(PoseStack pose, VertexConsumer consumer,
                                     float halfSize, float pushOut, float alpha, float[] col) {
        PoseStack.Pose mat = pose.last();
        var m4 = mat.pose();
        Vector3f cam = new Vector3f(-m4.m30(), -m4.m31(), -m4.m32());
        new Matrix3f(mat.normal()).transpose().transform(cam);

        float len = cam.length();
        float vx, vy, vz;
        if (len < 1e-4f) {
            vx = 0f; vy = 1f; vz = 0f;
        } else {
            vx = cam.x / len; vy = cam.y / len; vz = cam.z / len;
        }

        float ruX = 0f, ruY = 1f, ruZ = 0f;
        if (Math.abs(vy) > 0.99f) { ruX = 1f; ruY = 0f; ruZ = 0f; }
        // right = view × refUp
        float rx = vy * ruZ - vz * ruY;
        float ry = vz * ruX - vx * ruZ;
        float rz = vx * ruY - vy * ruX;
        float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1e-6f) return; // 视线与参考上向共线（退化）
        rx /= rl; ry /= rl; rz /= rl;
        // up = right × view
        float ux = ry * vz - rz * vy;
        float uy = rz * vx - rx * vz;
        float uz = rx * vy - ry * vx;

        float cx = vx * pushOut, cy = vy * pushOut, cz = vz * pushOut;
        float h = halfSize;
        float r = col[0], g = col[1], b = col[2];
        vertexN(mat, consumer, cx - rx * h - ux * h, cy - ry * h - uy * h, cz - rz * h - uz * h, r, g, b, alpha, 0f, 1f, vx, vy, vz);
        vertexN(mat, consumer, cx + rx * h - ux * h, cy + ry * h - uy * h, cz + rz * h - uz * h, r, g, b, alpha, 1f, 1f, vx, vy, vz);
        vertexN(mat, consumer, cx + rx * h + ux * h, cy + ry * h + uy * h, cz + rz * h + uz * h, r, g, b, alpha, 1f, 0f, vx, vy, vz);
        vertexN(mat, consumer, cx - rx * h + ux * h, cy - ry * h + uy * h, cz - rz * h + uz * h, r, g, b, alpha, 0f, 0f, vx, vy, vz);
    }

    /**
     * UV 球网格（加算球壳用这个）。单位球坐标即球面法线 —— 走 entity 系 RenderType 时
     * 保留真实受光朝向。配纯白纹理时 uv 恒取中心纹素，颜色完全由顶点色决定。
     *
     * 顶点顺序：(phi0,th0) → (phi1,th0) → (phi1,th1) → (phi0,th1)。
     *
     * @param rings    极向分段
     * @param segments 环向分段
     */
    public static void sphere(PoseStack pose, VertexConsumer consumer,
                              float radius, float[] col, float alpha,
                              int rings, int segments) {
        emitSphere(pose, consumer, radius, col, alpha, rings, segments, false);
    }

    /**
     * {@link #sphere} 的 POSITION_COLOR_TEX 版：只发位置/颜色/uv（无 overlay/uv2/normal），
     * 配自定义 shader RenderType 使用。
     *
     * ⚠️ uv 是**球面展开**（u = 环向 s/segments，v = 极向 r/rings），而非 {@link #sphere} 的
     * 恒 (0.5,0.5)。原因：hash 颗粒类 shader 以 uv 为噪点坐标，恒定 uv 会让整个球面
     * 采到同一个噪点值 —— 只剩整体亮度抖动，没有表面颗粒。展开后噪点才随球面位置变化。
     *
     * ⚠️ 发射链中不能出现 overlay / uv2 / normal（顶点格式不含这些元素）。
     */
    public static void sphereTex(PoseStack pose, VertexConsumer consumer,
                                 float radius, float[] col, float alpha,
                                 int rings, int segments) {
        emitSphere(pose, consumer, radius, col, alpha, rings, segments, true);
    }

    private static void emitSphere(PoseStack pose, VertexConsumer consumer,
                                   float radius, float[] col, float alpha,
                                   int rings, int segments, boolean texFormat) {
        if (radius <= 0f || rings < 1 || segments < 3) return;
        PoseStack.Pose mat = pose.last();
        int rows = rings + 1, cols = segments + 1;
        // 扁平网格：只分配一个 float[]，避免每帧大量小数组
        float[] grid = new float[rows * cols * 3];
        for (int r = 0; r < rows; r++) {
            float phi = (float) Math.PI * r / rings;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            for (int s = 0; s < cols; s++) {
                float th = 2f * (float) Math.PI * s / segments;
                int i = (r * cols + s) * 3;
                grid[i] = sp * Mth.cos(th);
                grid[i + 1] = cp;
                grid[i + 2] = sp * Mth.sin(th);
            }
        }
        float cr = col[0], cg = col[1], cb = col[2];
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                emitSphereVert(mat, consumer, grid, r, s, rows, cols, radius, cr, cg, cb, alpha, texFormat);
                emitSphereVert(mat, consumer, grid, r + 1, s, rows, cols, radius, cr, cg, cb, alpha, texFormat);
                emitSphereVert(mat, consumer, grid, r + 1, s + 1, rows, cols, radius, cr, cg, cb, alpha, texFormat);
                emitSphereVert(mat, consumer, grid, r, s + 1, rows, cols, radius, cr, cg, cb, alpha, texFormat);
            }
        }
    }

    private static void emitSphereVert(PoseStack.Pose mat, VertexConsumer consumer,
                                       float[] grid, int r, int s, int rows, int cols, float radius,
                                       float cr, float cg, float cb, float alpha, boolean texFormat) {
        int i = (r * cols + s) * 3;
        float nx = grid[i], ny = grid[i + 1], nz = grid[i + 2];
        if (texFormat) {
            // 球面展开 uv：u = 环向 s/segments，v = 极向 r/rings
            vertexTexCol(mat, consumer, nx * radius, ny * radius, nz * radius,
                cr, cg, cb, alpha, (float) s / (cols - 1), (float) r / (rows - 1));
        } else {
            // 单位球坐标即球面法线；配纯白纹理时 uv 恒取中心纹素
            vertexN(mat, consumer, nx * radius, ny * radius, nz * radius,
                cr, cg, cb, alpha, 0.5f, 0.5f, nx, ny, nz);
        }
    }

    // ==================================================================
    // 数值工具
    // ==================================================================

    /** 颜色线性插值（返回新数组；每帧少量分配可接受，热路径请缓存结果） */
    public static float[] mixCol(float[] a, float[] b, float t) {
        return new float[] {
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }

    /**
     * 高度消融曲线（余弦平方）：底部斜率为 0 → 贴地段保持实心；
     * 中段加速虚化（50% 高度处 ≈ 25% 强度）；
     * 顶部斜率为 0 地收敛到 0 → 自然消融，无硬切边。
     */
    public static float heightFade(float t) {
        float c = 0.5f * (1f + Mth.cos(Mth.PI * Mth.clamp(t, 0f, 1f)));
        return c * c;
    }

    /**
     * 把「已 ×255 的通道值」截断到 [0,255]，写入 NativeImage 前使用。
     * ⚠️ 入参必须是通道值而非归一化 0~1：曾因漏乘 255 使纹理 alpha 从 255 变 1，光墙整体消失。
     */
    public static int channel255(float value0to255) {
        return Mth.clamp((int) value0to255, 0, 255);
    }

    // ==================================================================
    // 顶点底座
    // ==================================================================

    /** 统一顶点发射：NEW_ENTITY 全量属性（color/uv/overlay/fullbright+向上法线） */
    private static void vertex(PoseStack.Pose mat, VertexConsumer consumer,
                               float x, float y, float z, float r, float g, float b, float alpha, float u, float v) {
        consumer.vertex(mat.pose(), x, y, z)
            .color(r, g, b, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(mat.normal(), 0, 1, 0)
            .endVertex();
    }

    /**
     * 带自定义法线的顶点发射。billboard / 球面等需要真实受光朝向的元素必须走这个版本
     * —— 私有 {@link #vertex} 的法线硬编码为 (0,1,0)，套上去会改变明暗。
     */
    private static void vertexN(PoseStack.Pose mat, VertexConsumer consumer,
                                float x, float y, float z, float r, float g, float b, float alpha,
                                float u, float v, float nx, float ny, float nz) {
        consumer.vertex(mat.pose(), x, y, z)
            .color(r, g, b, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(mat.normal(), nx, ny, nz)
            .endVertex();
    }

    /** 精简顶点发射：POSITION_COLOR_TEX 三属性（配自定义 shader RenderType） */
    private static void vertexTex(PoseStack.Pose mat, VertexConsumer consumer,
                                  float x, float y, float z, float alpha, float u, float v) {
        consumer.vertex(mat.pose(), x, y, z)
            .color(1f, 1f, 1f, alpha)
            .uv(u, v)
            .endVertex();
    }

    /**
     * {@link #vertexTex} 的带色版。纯白占位纹理 + 顶点色着色的元素必须走这个
     * —— {@link #vertexTex} 的颜色恒为白，会把顶点色吃掉。
     */
    private static void vertexTexCol(PoseStack.Pose mat, VertexConsumer consumer,
                                     float x, float y, float z,
                                     float r, float g, float b, float alpha, float u, float v) {
        consumer.vertex(mat.pose(), x, y, z)
            .color(r, g, b, alpha)
            .uv(u, v)
            .endVertex();
    }
}

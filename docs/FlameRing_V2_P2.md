# Flame Ring V2 P2 — 三材质火焰与 Edge Burn

本阶段把 V2 P1 的三层 Ribbon 从“同一张纹理、不同顶点颜色”升级为真正分附件材质。

## 渲染结构

fxFBO 现在使用 4 个 RGBA8 color attachments：CA0 保留公共后处理；CA1/CA2/CA3 分别存 Outer / Body / Core。火环在 AFTER_LEVEL 先分别写入三个附件，再由 FlameRingPass 统一采样。

## 材质处理

FlameRingPass 的 GLSL 对三层使用不同强度的 UV 扭曲，并重新做颜色响应：

- Outer：绯红外焰，较强扩散感。
- Body：橘红主焰，作为主要火焰体。
- Core：白金炽核，保持最高亮度。
- Edge Burn：根据外焰 alpha 梯度增加边缘热亮。
- Core Halo：根据核心邻域 alpha 轻微扩散白热光。

## 兼容路径

后处理关闭时仍使用原来的单 RenderType fallback，不改变服务器同步、Reveal Curve、实体寿命与粒子火星行为。

## 验证重点

进入游戏后重点观察三件事：外焰是否过红、核心是否过亮、Bloom 是否把整个圆环糊成一圈光。如果过曝，优先调低 flame shader 中三个 material intensity，不先修改实体时间轴。

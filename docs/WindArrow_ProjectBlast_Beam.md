# 风矢穿林：拖尾渲染与 Comet 思路对照

## 最新实测问题

Minecraft 截图中可见弧环，但主束只剩极细白线。原因不是单纯 alpha 太低：此前的拖尾是固定在局部 XY 平面的贴图四边形，之后又经过 yaw/pitch 旋转；摄像机从斜角看过去时，四边形容易缩成窄边。同时风矢攻击由射线立即结算，并没有真正移动的 projectile 供渲染器采样历史位置。

## 本轮修复

- 将主束拖尾改为 **camera-facing ribbon**：按当前相机位置逐段计算横向向量，避免不同视角下带宽塌缩成一条线。
- 将拖尾分为低亮蓝色外层、蓝色主体和窄白青核心；半宽由旧尾向前锋递增，透明度也由尾端向前锋递增。
- 只在模拟前锋后方约 6–10 格内绘制高亮拖尾，并在长度方向做轻微 S 形偏移；全路径仍以低亮星座贴图和节点余痕表达。
- 银白弧带与星芒仍是独立层，没有用宽实体柱来“补亮度”。

## CometSpell 源码分析

参考项目的 `CometSpell.java` 主要负责配置和生成 `CometProjectile`：它设置 `TRAIL_STYLE` 的长度、头尾宽度、点缀概率、历史采样上限和螺旋丝参数，再通过 `GlintstoneCastHelper.spawnAlongLook` 创建投射物。

真正的拖尾由下列代码协作完成：

1. `AbstractGlintstoneProjectile.trailParticles()` 在客户端每 Tick 记录弹体当前位置。
2. `TrailHistoryBuffer` 以世界坐标保存历史点，并按最大长度和点数裁剪。
3. `GlintstoneProjectileRenderer` 将插值弹头位置和历史点交给 `GlintstoneTrailRenderer`。
4. `GlintstoneTrailRenderer.renderHistoryRibbon()` 对轨迹平滑，逐段计算朝向相机的侧向量，再画半透明外层和窄亮芯；螺旋细丝另行沿历史曲线生成。
5. `GlintstoneTrailRenderTypes` 使用透明与加法材质分层，避免单层纯色几何变成塑料棍。

参考源码：
- [CometSpell.java](https://github.com/shuimo0413/Iron-Magic-Elden-Ring/blob/main/src/main/java/com/eldenring/spells/spell/CometSpell.java)
- [AbstractGlintstoneProjectile.java](https://github.com/shuimo0413/Iron-Magic-Elden-Ring/blob/main/src/main/java/com/eldenring/spells/entity/AbstractGlintstoneProjectile.java)
- [GlintstoneTrailRenderer.java](https://github.com/shuimo0413/Iron-Magic-Elden-Ring/blob/main/src/main/java/com/eldenring/spells/client/render/glintstone/GlintstoneTrailRenderer.java)
- [TrailHistoryBuffer.java](https://github.com/shuimo0413/Iron-Magic-Elden-Ring/blob/main/src/main/java/com/eldenring/spells/entity/TrailHistoryBuffer.java)

风矢目前用射线即时命中，不适合原封不动复制 Comet 的实体历史缓冲；本轮先复用其最关键的 camera-facing ribbon 算法，并用视觉年龄模拟前锋推进。若未来将风矢改为真正移动的视觉 projectile，再引入世界坐标历史缓冲即可自然支持转弯轨迹。

## 范围与验收

- 仅更改 `LizhiYanBlastRenderer` 和本说明，不改变主/副箭索敌、伤害、法强、元素消耗、猎矢返还、冷却或 Recast。
- 默认 `STYLE_BLAST`（理之言/破晦阵）保持原样。
- [ ] 远景下主束不再塌成细线；亮芯与柔和蓝色外层可分辨。
- [ ] 拖尾尾部逐渐变细、变暗，前锋附近更亮、更宽。
- [ ] 斜视/侧视时仍能看到带宽。
- [ ] 星座连线与银白弧带不遮住主束。
- [ ] 命中闪光仍在视觉前锋抵达后出现。

本轮已提交源码，但尚未在此环境运行 ForgeGradle 或启动 Minecraft；需要在本地编译和同角度实测后校准尺寸。

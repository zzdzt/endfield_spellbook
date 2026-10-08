package com.zzdzt.endfield_spellbook.client.post;

import com.zzdzt.endfield_spellbook.spell.gloompurge.GloompurgeMarkEntity;

/**
 * 实体 pass 冻结的破晦阵渲染参数快照。
 *
 * 实体 pass 只收集不绘制；AFTER_LEVEL 阶段按快照重绘到自研 FBO。
 * f / alpha（含 fade 折算）/ dist 在实体 pass 算好冻结，保证与直接绘制
 * 的视觉参数完全一致（同帧同 partialTick，无跨帧问题）。
 */
public record MarkSnapshot(GloompurgeMarkEntity entity, float f, float alpha, double dist, int shape) {
}

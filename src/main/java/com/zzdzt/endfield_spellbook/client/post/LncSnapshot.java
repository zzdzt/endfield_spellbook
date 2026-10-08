package com.zzdzt.endfield_spellbook.client.post;

import com.zzdzt.endfield_spellbook.spell.liquidnitrogencannon.LncProjectileEntity;

/**
 * 实体 pass 冻结的液氮炮弹渲染参数快照。
 *
 * 实体 pass 只收集不绘制；AFTER_LEVEL 阶段按快照重绘到自研 FBO，
 * 与破晦阵 MarkSnapshot 共用一条管线与 bloom。
 */
public record LncSnapshot(LncProjectileEntity entity, float f) {
}

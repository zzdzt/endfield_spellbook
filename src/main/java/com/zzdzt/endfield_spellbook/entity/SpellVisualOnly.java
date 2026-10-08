package com.zzdzt.endfield_spellbook.entity;

/**
 * 纯视觉演出实体标记（无伤害判定、无游戏逻辑）。
 *
 * 这类实体由 tickCount 驱动演出时长，不应被"少 Tick"类缓速效果冻结——
 * 冻结会与法术真实时长错位（如印记提前/延迟消散），且没有任何玩法收益。
 */
public interface SpellVisualOnly {
}

package com.zzdzt.endfield_spellbook.entity;

/**
 * Tick-locked spell-effect marker.
 *
 * Visual effects and their synchronized server-side controllers use tickCount to coordinate
 * timing. They must not be frozen by "fewer ticks" slow effects or their choreography can drift.
 * This marker does not itself imply that an entity has no gameplay logic.
 */
public interface SpellVisualOnly {
}

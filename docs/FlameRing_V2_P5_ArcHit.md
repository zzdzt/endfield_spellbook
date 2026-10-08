# Flame Ring V2 P5 — Server-authoritative fan-sector hits

## Implemented behavior

- The phantom blade, ring particles/ribbon, and attack controller share `FlameRingGeometry` for the view-plane basis, start-angle conversion, arc points and tangent.
- `FlameRingCastCurve.revealedDegrees(...)` remains the single timing curve. The server checks only the newly revealed angular sector between the previous and current tick.
- Each sampled angle tests a radial segment from the ring center (player-side origin) to the outer radius. The sector is therefore filled from inside to the displayed arc rather than checking only the thin circumference.
- Sampling density is based on the outer arc length (about 0.22 blocks per angular step at the outer radius). Target bounding boxes are inflated by 0.48 blocks to account for hitbox/fire thickness.
- Each target UUID is registered before attempting damage, so overlapping rays and later propagation cannot hit that target twice during the same cast.
- Hits use ISS `DamageSources.applyDamage`; successful hits retain the fire-particle feedback and `EnchantmentHelper.doPostDamageEffects`.
- The controller stops after the last propagation sector. As the reveal reaches 360 degrees, the filled sectors sweep the complete disk; full-ring burn and breakup remain visual-only.
- Existing heat absorption, molten-fire stack accumulation/consumption, enhanced explosion and enhanced damage multiplier remain in `SmoulderingFireSpell`.
- If no qualifying slash weapon is present, the original immediate area-damage path remains as a compatibility fallback; casts that spawn the blade/ring use fan-sector damage.

## Acceptance checklist

- [ ] A target between the player-side center and outer flame arc can be hit when a newly revealed sector crosses it.
- [ ] A target inside the disk but outside the currently revealing sector waits until the sweep reaches its angle.
- [ ] A target hit during the blade sweep is not hit again during propagation.
- [ ] Once all sectors finish revealing the full disk, no additional damage occurs during full-ring burn or breakup.
- [ ] Large target bounding boxes can contact the filled fan sector.
- [ ] Main-hand/off-hand mirrored direction and looking up/down align collision with the ring plane.
- [ ] Heat absorption, molten-fire resources, enhanced damage, ISS damage events/resistances and post-hit enchantment effects remain intact.
- [ ] ForgeGradle build and in-game collision tests pass.

## Verification status

Source-level checks were performed before commit. A local Gradle build and in-game collision test were not run in this environment; they remain required before considering the change fully validated.

# Flame Ring V2 P5 — Server-authoritative arc hits

## Implemented behavior

- The phantom blade, ring particles/ribbon, and attack controller share `FlameRingGeometry` for the view-plane basis, start-angle conversion, arc points and tangent.
- `FlameRingCastCurve.revealedDegrees(...)` remains the single timing curve. The server checks only the newly revealed segment between the previous and current tick.
- The controller samples the arc at a maximum chord spacing of about 0.22 blocks and checks bounding boxes with 0.48-block contact thickness.
- Each target UUID is registered before attempting damage, so overlapping samples and later propagation cannot hit that target twice during the same cast.
- Hits use ISS `DamageSources.applyDamage`; successful hits retain the fire-particle feedback and `EnchantmentHelper.doPostDamageEffects`.
- The controller stops after the last propagation segment. Full-ring burn and breakup stay visual-only.
- Existing heat absorption, molten-fire stack accumulation/consumption, enhanced explosion and enhanced damage multiplier remain in `SmoulderingFireSpell`.
- If no qualifying slash weapon is present, the original immediate area-damage path remains as a compatibility fallback; casts that spawn the blade/ring use arc-only damage.

## Acceptance checklist

- [ ] Targets touching a newly revealed arc take damage once.
- [ ] Targets inside the ring but not touching the arc take no arc damage.
- [ ] A target hit during the blade sweep is not hit again during propagation.
- [ ] Full-ring burn and breakup cause no additional damage.
- [ ] Large target bounding boxes can contact the flame band.
- [ ] Main-hand/off-hand mirrored direction and looking up/down align collision with the ring.
- [ ] Heat absorption, molten-fire resources, enhanced damage, ISS damage events/resistances and post-hit enchantment effects remain intact.
- [ ] ForgeGradle build and in-game collision tests pass.

## Verification status

Source-level checks were performed before commit. A local Gradle build and in-game collision test could not be run in this environment because cloning the repository over the shell network failed.

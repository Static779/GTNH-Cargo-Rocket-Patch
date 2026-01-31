# Future Development Phases

Ideas and enhancements for future versions of the GTNH Rocket Animation mod.

---

## Phase 2: Extended Rocket Support

### Patch All Rocket Types
Currently only `EntityCargoRocket` is patched. Extend support to:

- **Tier 1 Rocket** - `EntityTier1Rocket` (Moon rocket)
- **Tier 2 Rocket** - `EntityTier2Rocket` (Mars rocket)  
- **Tier 3 Rocket** - `EntityTier3Rocket` (Asteroids rocket)
- **Base class patch** - Alternatively, patch `EntityAutoRocket` to cover all rockets at once

### Implementation Notes
- The transformer can be extended to target multiple classes
- Most fields (`landing`, `targetVec`, `launchPhase`) are in the shared superclass
- May need per-rocket tuning for descent speeds based on rocket mass/tier

---

## Phase 3: Visual Enhancements

### Landing Pad Block Detection
Detect the actual landing pad block to get exact Y positioning instead of using `targetVec.y + 1.0`:

```java
// Pseudocode
Block padBlock = world.getBlock(targetVec.x, targetVec.y, targetVec.z);
if (padBlock instanceof BlockLandingPad) {
    targetY = targetVec.y + padBlock.getTopSurfaceOffset();
}
```

### Custom Particle Effects
Add enhanced particles during landing:

- **Retro-burn flames** - Downward-pointing flames during final descent
- **Dust/smoke on touchdown** - Particle burst when rocket lands
- **Heat distortion** - Wavy effect near engines (client-side shader if possible)

### Leg Deployment Animation
If the rocket model has landing legs:

- Deploy legs at a specific altitude (e.g., 20 blocks above pad)
- Animate leg extension over N ticks
- Sync with existing GC leg rendering if available

---

## Phase 4: Audio Enhancements

### Landing Sounds
- **Retro-burn sound** - Engine throttle-up sound during final descent
- **Touchdown thud** - Impact sound when rocket lands
- **Engine wind-down** - Gradual engine shutdown after landing

### Takeoff Sounds
- **Engine spool-up** - Matches the thrust ramp animation
- **Rumble intensity** - Sound volume/pitch tied to thrust level

### Implementation
- Use Minecraft's `SoundEvent` system
- Add sound files to `assets/gtnhrocketanim/sounds/`
- Create `sounds.json` for sound registration

---

## Phase 5: Advanced Physics

### Atmospheric Re-entry Effects
When arriving from space:

- Start with higher velocity at spawn
- Apply "aerobraking" deceleration curve
- Optional: heat shield visual effects (orange glow)

### Wind/Drift Simulation
- Add slight random horizontal drift during descent
- Rocket corrects more aggressively as it gets closer to pad
- Creates more realistic "active guidance" feel

### Fuel-Based Landing
- Consume fuel during landing burn (retro-rockets)
- If insufficient fuel, rocket crashes instead of landing safely
- Adds gameplay consequence to fuel management

---

## Phase 6: Configuration UI

### In-Game Config Screen
- Add Forge config GUI support
- Allow real-time tuning without restart
- Preview animations with test rocket entity

### Per-Dimension Settings
Different landing/takeoff behavior per dimension:

```ini
[dimension.moon]
landingSpawnHeight=80
maxDescentSpeed=0.8  # Lower gravity

[dimension.mars]
landingSpawnHeight=100
maxDescentSpeed=1.2  # Thinner atmosphere
```

---

## Phase 7: Multiplayer Enhancements

### Spectator View
- Other players see smooth landing animation
- Camera tracking option for dramatic views
- Optional "mission control" chat messages

### Landing Zone Protection
- Prevent entities from spawning in landing path
- Clear area around pad during descent
- Visual warning markers

---

## Phase 8: Integration

### Waila/HWYLA Support
Show rocket status on hover:
- "Landing - 45 blocks above pad"
- "Takeoff - 23% thrust"
- ETA to landing/departure

### JourneyMap Integration
- Show rocket trajectory on map
- Mark active landing zones
- Track cargo rocket destinations

---

## Technical Debt / Improvements

### Code Quality
- [ ] Add unit tests for easing curves
- [ ] Extract magic numbers to named constants
- [ ] Add more detailed logging for debugging
- [ ] Consider Mixin backport for cleaner patches

### Performance
- [ ] Profile tick hook overhead
- [ ] Optimize WeakHashMap usage in hooks
- [ ] Consider client-side prediction for smoother visuals

### Compatibility
- [ ] Test with other GC addons (Extra Planets, Galaxy Space)
- [ ] Verify behavior with GC space stations
- [ ] Check interaction with chunk loading

---

## Community Requests

*Space for tracking feature requests from users after initial release*

| Request | Priority | Status |
|---------|----------|--------|
| - | - | - |

---

## Version Roadmap

| Version | Features | Status |
|---------|----------|--------|
| 1.0.0 | Basic landing/takeoff animation for cargo rockets | ✅ Complete |
| 1.1.0 | All rocket tiers support | 📋 Planned |
| 1.2.0 | Enhanced particles and sounds | 📋 Planned |
| 2.0.0 | Advanced physics and fuel integration | 💡 Concept |

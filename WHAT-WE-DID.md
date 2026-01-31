# GTNH Rocket Landing/Takeoff Animation Patch

This project is a **separate addon mod** (a Forge 1.7.10 **coremod**) intended for **GregTech: New Horizons (GTNH)**,
targeting the **GTNH Galacticraft fork** (e.g. `Galacticraft-3.3.13-GTNH.jar`).

It patches the **Mars cargo rocket** class:

- `micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket`

so the rocket can **arrive above the pad and land smoothly** (instead of snapping to the pad),
and **take off with a smooth ease-in ramp**.

---

## The Problem

In the original Galacticraft code (GTNH fork), when a cargo rocket arrives at its destination:

1. Galacticraft calls `moveToDestination(800)` intending to spawn the rocket 800 blocks above the pad
2. **But** the method has a bug: it immediately overwrites the height parameter to 0 unless `destinationFrequency == 1`
3. The rocket teleports directly to the pad with no descent animation
4. Same issue on takeoff: the rocket immediately starts at full thrust with no ramp-up

This makes dimension travel feel jarring and unrealistic.

---

## Our Solution

We use ASM bytecode transformation (coremod) to:

### 1) Fix `moveToDestination(int)` to actually use its height argument

**Before (buggy):**
```java
private void moveToDestination(int reentryHeight) {
    if (this.destinationFrequency != 1) {
        reentryHeight = 0;  // BUG: throws away the height!
    }
    this.setPosition(targetVec.x + 0.5, targetVec.y + reentryHeight, targetVec.z + 0.5);
}
```

**After (patched):**
```java
private void moveToDestination(int reentryHeight) {
    // Route large values (800) through config, keep small values (2) as-is
    int resolved = RocketAnimHooks.resolveArrivalHeight(reentryHeight);
    
    this.setPosition(
        targetVec.x + 0.5,
        targetVec.y + resolved + (destinationFrequency == 1 ? 0 : 1),
        targetVec.z + 0.5
    );
}
```

**Result:** Rocket spawns at configurable height above the pad (default: 120 blocks).

### 2) Inject a post-tick hook for smooth landing/takeoff

We inject a call to `RocketAnimHooks.onCargoRocketTick(...)` at the end of the entity's tick method.

This hook:
- Runs **server-side only** (authoritative)
- During **landing**: applies smooth descent with horizontal drift correction
- During **takeoff**: applies thrust ramp-up for realistic spool effect

---

## Technical Details

### ASM Transformer

The transformer (`RocketAnimTransformer.java`) does two things:

1. **Replaces** the entire body of `moveToDestination(I)V`
2. **Injects** a method call before the `RETURN` in `func_70071_h_()V` (tick method)

### Field Locations

Some fields are inherited from parent classes:
- `targetVec`, `landing`, `destinationFrequency` → `EntityAutoRocket`
- `launchPhase`, `timeSinceLaunch` → `EntitySpaceshipBase` (via `EntityAutoRocket`)

The transformer accesses these via `GETFIELD` with the correct owner class.

### Easing Curves

We use **smootherstep** (Ken Perlin's improved version) for buttery-smooth animation:

```
smootherstep(t) = 6t⁵ - 15t⁴ + 10t³
```

This provides:
- Slow start and end (no sudden velocity changes)
- Faster movement in the middle
- Better feel than linear or basic smoothstep

### Two-Phase Landing

1. **High altitude (>30 blocks)**: Faster descent at ~60-100% of max speed
2. **Low altitude (<30 blocks)**: Progressive slowdown using smootherstep

This creates a realistic deceleration as the rocket approaches the pad.

---

## Configuration

After first run, edit `config/gtnhrocketanim.cfg`:

### Landing Settings
| Option | Default | Description |
|--------|---------|-------------|
| `landingSpawnHeight` | 120 | How high above the pad the rocket spawns |
| `maxDescentSpeed` | 1.0 | Max descent speed (blocks/tick) |
| `minDescentSpeed` | 0.04 | Min descent speed near pad |
| `horizontalCorrection` | 0.06 | How fast it centers over pad |
| `snapDistance` | 0.3 | Distance to snap to final position |

### Takeoff Settings
| Option | Default | Description |
|--------|---------|-------------|
| `takeoffRampTicks` | 80 | Ticks to reach full thrust (0 = disabled) |
| `takeoffMinMultiplier` | 0.10 | Initial thrust fraction |

### Debug Settings
| Option | Default | Description |
|--------|---------|-------------|
| `debugLogging` | false | Enable console logging |

---

## Files in This Project

```
gtnhrocketanim/
├── build.gradle              # ForgeGradle 1.2 build script
├── gradle.properties         # Gradle settings
├── settings.gradle           # Project name
├── README.md                 # Quick start guide
├── WHAT-WE-DID.md            # This file
└── src/main/
    ├── java/com/yourname/gtnhrocketanim/
    │   ├── RocketAnimMod.java         # Mod container, loads config
    │   ├── RocketAnimConfig.java      # Configuration handler
    │   ├── RocketAnimHooks.java       # Landing/takeoff logic
    │   └── core/
    │       ├── RocketAnimCorePlugin.java    # FML coremod entry
    │       └── RocketAnimTransformer.java   # ASM bytecode patcher
    └── resources/
        └── mcmod.info                 # Mod metadata
```

---

## Why a Coremod?

The behavior we need to change is inside Galacticraft's entity logic. GTNH ships the forked mod as a jar,
and we don't want to edit that jar directly. A coremod lets us:

- Modify bytecode at load-time (ASM)
- Keep it as a separate mod (drop-in/out)
- Avoid maintaining a custom Galacticraft jar
- Work with any compatible GTNH version

---

## Build Instructions

### Prerequisites
- Java 8 JDK (required for 1.7.10)
- Gradle (or use the wrapper)

### Building
```bash
cd gtnhrocketanim
./gradlew build
```

The jar will be in `build/libs/gtnhrocketanim-1.0.0.jar`.

### Installing
1. Copy the jar to your GTNH `mods/` folder
2. Launch the game once to generate config
3. Adjust `config/gtnhrocketanim.cfg` as desired

---

## Limitations / Future Improvements

- **Only patches EntityCargoRocket** - Other rocket types (Tier 1/2/3, etc.) could be added
- **Landing Y offset** - Uses `targetVec.y + 1.0` as default; could detect actual pad block height
- **No particle enhancements** - Could add custom landing flame effects
- **No sound modifications** - Could add landing burn / touchdown sounds

---

## Compatibility

- **Minecraft:** 1.7.10
- **Forge:** 10.13.4.1614+ 
- **GTNH Galacticraft:** 3.3.13-GTNH (tested)
- **Other GC versions:** Should work with similar GTNH forks, may need field name adjustments

---

## ASM vs Mixin

This project uses **ASM** (not Mixin) because:

1. Mixin didn't exist natively in 1.7.10's modding ecosystem
2. GTNH traditionally uses ASM coremods
3. Adding Mixin would introduce extra dependencies and potential conflicts
4. ASM gives us complete control over bytecode for this specific patch

For modern Minecraft (1.13+), Mixin would be the preferred approach.

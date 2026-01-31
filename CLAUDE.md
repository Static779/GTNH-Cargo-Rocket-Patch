# GTNH Rocket Animation Mod - Claude Code Context

## Project Overview
A Minecraft 1.7.10 Forge coremod that adds visual takeoff and landing animations to Galacticraft cargo rockets in GT New Horizons (GTNH) modpack.

## What This Mod Does
- **Takeoff Animation**: Rockets visually fly upward from the launchpad before teleporting to destination
- **Landing Animation**: Rockets descend smoothly to the landing pad instead of appearing instantly
- **Particle Effects**: GC-style flame particles during takeoff and landing

## Architecture

### Core Files
- `RocketAnimHooks.java` - Main entry points called by ASM-injected hooks
- `RocketAnimTransformer.java` - ASM bytecode transformer that patches GC classes
- `RocketAnimCorePlugin.java` - Forge coremod bootstrap
- `RocketStateTracker.java` - Tracks rocket state across ticks (HashMap by entity ID)
- `LandingHandler.java` - Handles smooth landing descent physics
- `RocketParticles.java` - Particle effects using GC's particle system
- `RocketAnimConfig.java` - Configuration options

### How It Works
1. **ASM Patches** (in `RocketAnimTransformer.java`):
   - Patches `EntityCargoRocket.onReachAtmosphere()` - Adds guard at start to block teleportation until altitude threshold reached
   - Patches `EntityCargoRocket.func_70071_h_()` (tick) - Injects hook at end for our animation logic
   - Patches `EntityCargoRocket.moveToDestination()` - Modifies spawn height for landing

2. **Takeoff Flow**:
   - GC calls `onReachAtmosphere()` every tick when rocket is launched
   - Our guard (`shouldDelayAtmosphereTransition()`) returns true to BLOCK until Y >= threshold
   - Our tick hook manually drives rocket upward with quadratic acceleration
   - When threshold reached, guard returns false, GC teleports rocket normally

3. **Landing Flow**:
   - Rocket spawns at configured height (default 120 blocks above pad)
   - `LandingHandler` applies deceleration curve (sqrt-based) for smooth descent
   - Snaps to pad position when close enough

## Key Configuration (RocketAnimConfig.java)
- `takeoffAltitudeThreshold` = 350.0 (blocks before teleport)
- `landingSpawnHeight` = 120 (blocks above pad to spawn)
- `maxDescentSpeed` / `minDescentSpeed` for landing curve

## Acceleration Curve (EDIT THIS)

Located in `RocketAnimHooks.java` around line 200:

```java
// === QUADRATIC CURVE (^2) WITH ADDITIVE SHIFT ===
// Decent initial speed with smooth acceleration
double baseSpeed = 0.08;  // Starting speed (blocks/tick)
double accelFactor = 0.8; // How aggressively speed increases

// Quadratic curve: (1 + progress * accelFactor)^2
double speedMultiplier = Math.pow(1.0 + progress * accelFactor, 2.0);

// Additive shift: +0.5 blocks every 20 ticks (0.025 per tick)
double additiveShift = (timeSinceLaunch / 20.0) * 0.5;

// Initial engine spool-up ramp
double launchRamp = Math.min(timeSinceLaunch / 40.0, 1.0); // Ramp over 2 seconds

double upwardSpeed = (baseSpeed * speedMultiplier + additiveShift) * launchRamp;
```

### Formula
```
speed = (baseSpeed × (1 + progress × accelFactor)² + additiveShift) × launchRamp
```

### Speed Chart (blocks/second)
| Progress | Ticks | Speed |
|----------|-------|-------|
| 0%       | 40    | 1.6   |
| 25%      | 60    | 5.2   |
| 50%      | 80    | 12.0  |
| 75%      | 100   | 22.4  |
| 100%     | 120   | 37.2  |

### To Adjust:
- **baseSpeed**: Initial speed at launch (0.08 = 1.6 blocks/second)
- **accelFactor**: Controls curve steepness (higher = faster acceleration)
- **Exponent (2.0)**: The power - change to 1.5 for gentler, 3.0 for more aggressive
- **additiveShift**: Linear time-based speed boost (+0.5 blocks/tick every 20 ticks)

### Curve Options:
| Exponent | Formula | Behavior |
|----------|---------|----------|
| 1.0 | linear | Constant acceleration |
| 1.5 | power 1.5 | Gentle acceleration |
| **2.0** | **quadratic** | **Standard acceleration (current)** |
| 3.0 | cubic | Aggressive acceleration |

## Build & Deploy
```powershell
cd gtnhrocketanim
.\gradlew build
Copy-Item ".\build\libs\gtnhrocketanim-1.0.0.jar" "PATH_TO_GTNH\.minecraft\mods\" -Force
```

## Target Class Reference
The mod patches `micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket`.
See `EntityCargoRocket.java` in workspace root for decompiled source reference.

## Server Configuration

Config file location: `config/gtnhrocketanim.cfg`

### Debug Logging
Debug logging is **disabled by default** for production. To enable verbose logging:

```cfg
debug {
    B:debugLogging=true
}
```

When enabled, logs include:
- Takeoff detection and progress (every 20 ticks)
- Altitude threshold transitions
- Landing spawn height modifications
- Tick status (every 100 ticks)

### Key Config Options
```cfg
takeoff {
    I:takeoffAltitudeThreshold=350    # Y-level before teleport
}

landing {
    I:landingSpawnHeight=450          # Height above pad to spawn
    D:maxDescentSpeed=0.8             # Max fall speed (blocks/tick)
    D:minDescentSpeed=0.03            # Min fall speed near pad
}

particles {
    B:enableRetrogradeBurn=true       # Landing flame particles
    B:enableTakeoffParticles=true     # Takeoff exhaust particles
    D:particleIntensity=1.0           # Particle count multiplier
}
```

## Troubleshooting
- If rockets teleport instantly: Enable `debugLogging=true` and check if `shouldDelayAtmosphereTransition` is being called
- If rockets don't move: Check server-side tick hook is applying `posY` changes
- If no particles: Particles are CLIENT-side only - check `!w.isRemote` conditions
- Old corrupted rockets (Y=NaN): Delete old entities or start new world
- For detailed diagnostics: Set `debugLogging=true` in config and check server console

# GTNH Rocket Animation Mod

A Forge coremod for **GT New Horizons** (Minecraft 1.7.10) that adds smooth, cinematic landing and takeoff animations to Galacticraft cargo rockets.

![Rocket Landing Demo](gif.gif)

## 🚀 Features

### Takeoff Animation
- Rockets no longer teleport instantly when launched
- **Quadratic acceleration curve** — starts slow, builds speed realistically
- Visible engine exhaust using Galacticraft's native particle system
- Rockets ascend smoothly until reaching configurable altitude threshold, then transition to destination

### Landing Animation
- Rockets spawn high above the destination pad (configurable, default 450 blocks)
- **Physics-based descent** with square-root deceleration curve
- Fast approach at high altitude, gentle touchdown near the pad
- Retrograde burn particles during descent
- Dust cloud effect on touchdown
- Automatic horizontal correction to center on landing pad

### Visual Effects
- Uses **Galacticraft's native flame particles** (`EntityFXLaunchFlame`) for authentic visuals
- Grid-based particle spawn pattern for realistic exhaust plume spread
- Configurable particle intensity
- Server-client synchronized — works in multiplayer

## 📦 Installation

1. **Requirements:**
   - Minecraft 1.7.10
   - Forge 10.13.4.1614+
   - GT New Horizons modpack (or Galacticraft 3)

2. **Install:**
   - Download the latest release JAR
   - Place in your `mods/` folder
   - Launch the game

3. **Configure (optional):**
   - Edit `config/gtnhrocketanim.cfg` after first launch

## ⚙️ Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `landingSpawnHeight` | 450 | Height above pad where rockets spawn for landing |
| `takeoffAltitudeThreshold` | 350 | Y-level at which rockets teleport to destination |
| `maxDescentSpeed` | 0.8 | Maximum descent speed (blocks/tick) |
| `minDescentSpeed` | 0.03 | Minimum descent speed near touchdown |
| `horizontalCorrection` | 0.3 | Max horizontal drift correction per tick |
| `snapDistance` | 0.5 | Distance from pad to snap into place |
| `enableRetrogradeBurn` | true | Show particles during descent |
| `enableTouchdownParticles` | true | Show dust cloud on landing |
| `enableTakeoffParticles` | true | Show exhaust during takeoff |
| `particleIntensity` | 1.0 | Particle count multiplier (0.0–2.0) |
| `debugLogging` | false | Enable verbose logging for troubleshooting |

## 🔧 Technical Details

This mod uses **ASM bytecode transformation** (coremod) to patch two methods in `EntityCargoRocket`:

1. **`moveToDestination(int)`** — Intercepted to capture destination coordinates and initiate takeoff animation instead of instant teleport

2. **`onUpdate()`** — Hook injected at the end to apply custom motion physics each tick

### Architecture

```
RocketAnimHooks.java           ← ASM hook entry points
├── RocketStateTracker.java    ← Entity state management (HashMaps)
├── TakeoffHandler.java        ← Takeoff physics & teleport trigger
├── LandingHandler.java        ← Landing physics & snap-to-pad
└── RocketParticles.java       ← GC-native particle effects via reflection
```

### Physics

**Takeoff:** `speed = k × t²` — Smooth quadratic acceleration from near-zero

**Landing:** `speed = min + (max - min) × √(height / 100)` — Square-root deceleration for gentle touchdown

## 🛠️ Building from Source

```bash
# Clone the repository
git clone https://github.com/Static779/GTNH-Cargo-Rocket-Patch.git
cd GTNH-Cargo-Rocket-Patch

# Build with Gradle (uses RetroFuturaGradle)
./gradlew build

# Output JAR in build/libs/
```

Requires: Java 17+ (RetroFuturaGradle auto-downloads Java 8 for compilation)

## 📋 Compatibility

- ✅ GT New Horizons 2.8.x
- ✅ Galacticraft 3 (GC3)
- ✅ Single-player and multiplayer
- ✅ Dedicated servers
- ⚠️ Other Galacticraft addons — should work, but untested

## 📄 License

MIT License — Feel free to use, modify, and redistribute.

## 🙏 Credits

- **Galacticraft Team** — For the original rocket entity and particle system
- **GTNH Team** — For the amazing modpack
- **RetroFuturaGradle** — Modern build toolchain for 1.7.10 mods

See [WHAT-WE-DID.md](WHAT-WE-DID.md) for technical details.

## Building from Source

```bash
# Clone the repo
git clone <repo-url>
cd gtnhrocketanim

# Build (requires Java 8)
./gradlew build

# Output jar will be in build/libs/
```

## License

Open source - feel free to use and modify.

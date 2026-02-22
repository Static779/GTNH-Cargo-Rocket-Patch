package com.yourname.gtnhrocketanim;

/**
 * Defines properties for each tier of cargo rocket, mirroring the GTNH rocket progression.
 *
 * Tier is determined by the EntityCargoRocket's rocketType field ordinal:
 *   ordinal 0 (DEFAULT)    -> T1 ( 9 slots,  1000 mB,  Rocket Fuel)
 *   ordinal 1 (INVENTORY27)-> T2 (27 slots,  1500 mB,  Rocket Fuel)  [legacy default]
 *   ordinal 2 (INVENTORY36)-> T3 (54 slots,  2000 mB,  Rocket Fuel)
 *   ordinal 3 (INVENTORY54)-> T4 (81 slots,  3000 mB,  Dense Hydrazine)
 *
 * For T5-T8, craft the appropriate tier item (requires CraftTweaker recipe) and the
 * "GTNHCargoTier" NBT tag (int 4-7) will be set on the rocket entity.
 *
 * All animation params and config overrides are accessed via RocketAnimConfig.
 */
public enum CargoRocketTier {

    // ------------------------------------------------------------------
    //  Tier,  slots,  fuelCap(mB), fuelInterval(ticks),
    //         baseSpeed, accelFactor, maxAscent(blk/t), maxDescent(blk/t),
    //         defaultTexturePath,
    //         defaultAllowedDimensions (null = unrestricted)
    // ------------------------------------------------------------------

    /** T1 — Moon-class cargo rocket (mirrors GC Tier-1 rocket). */
    T1(9,   1000, 3,
       0.05, 0.5, 6.0,  0.6,
       "gtnhrocketanim:textures/model/cargoRocketT1.png",
       new int[]{ 28 }),                    // Moon only

    /** T2 — Mars-class cargo rocket (mirrors GC Tier-2 / existing cargo rocket). */
    T2(27,  1500, 2,
       0.08, 0.8, 8.0,  0.8,
       "galacticraftmars:textures/model/cargoRocket.png",
       new int[]{ 28, 29 }),               // Moon + Mars

    /** T3 — Asteroid-class cargo rocket (mirrors GC Tier-3). */
    T3(54,  2000, 2,
       0.10, 1.0, 10.0, 1.0,
       "gtnhrocketanim:textures/model/cargoRocketT3.png",
       new int[]{ 28, 29, 30 }),           // + Asteroids (dim 30)

    /** T4 — Inner solar system cargo (GalaxySpace Tier-4, Dense Hydrazine). */
    T4(81,  3000, 1,
       0.13, 1.3, 12.0, 1.3,
       "gtnhrocketanim:textures/model/cargoRocketT4.png",
       // Mercury=37, Phobos=38, Venus=39, Deimos=40, Ceres=42, Jupiter=43, Io=45
       new int[]{ 28, 29, 30, 37, 38, 39, 40, 42, 43, 45 }),

    /** T5 — Jupiter/Saturn belt cargo (GalaxySpace Tier-5, Purple Fuel CN3H7O3). */
    T5(108, 4000, 1,
       0.16, 1.6, 14.0, 1.6,
       "gtnhrocketanim:textures/model/cargoRocketT5.png",
       // + Ganymede=35, Europa=36, Saturn=41, Callisto=44
       new int[]{ 28, 29, 30, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45 }),

    /** T6 — Uranus/Neptune belt cargo (GalaxySpace Tier-6, Purple Fuel CN3H7O3). */
    T6(135, 5000, 1,
       0.20, 2.0, 16.0, 2.0,
       "gtnhrocketanim:textures/model/cargoRocketT6.png",
       // + Titan=46, Neptune=47, Neptune moons=48, Uranus=86
       new int[]{ 28, 29, 30, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 86 }),

    /** T7 — Dwarf-planet / Kuiper belt cargo (GalaxySpace Tier-7, Green Fuel H8N4C2O4). */
    T7(162, 6000, 1,
       0.25, 2.5, 18.0, 2.5,
       "gtnhrocketanim:textures/model/cargoRocketT7.png",
       // + Makemake=25, Pluto=49, Haumea=83, Vega-B=84, T-Ceti-E=85
       new int[]{ 25, 28, 29, 30, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 83, 84, 85, 86 }),

    /** T8 — Interstellar cargo (GalaxySpace Tier-8, Green Fuel H8N4C2O4). */
    T8(189, 8000, 1,
       0.30, 3.0, 20.0, 3.0,
       "gtnhrocketanim:textures/model/cargoRocketT8.png",
       null);   // null = no dimension restriction

    // ------------------------------------------------------------------
    //  Fields
    // ------------------------------------------------------------------

    /** Number of inventory slots. */
    public final int slotsCount;

    /** Raw fuel tank capacity in mB (multiplied by rocketFuelFactor in-game). */
    public final int fuelCapacity;

    /**
     * Ticks between each 1-unit fuel-drain event.
     *   3 = T1/T2/T3 speed (slower, matches GC Tier-1/2 rate)
     *   1 = T4+ speed (every tick, matches GalaxySpace high-tier rate)
     */
    public final int fuelTickInterval;

    /** Starting speed coefficient used in the takeoff acceleration formula. */
    public final double baseSpeed;

    /** Acceleration steepness factor used in the takeoff acceleration formula. */
    public final double accelFactor;

    /** Hard cap on upward speed (blocks/tick) during takeoff animation. */
    public final double maxAscentSpeed;

    /** Maximum downward speed (blocks/tick) during landing animation. */
    public final double maxDescentSpeed;

    /**
     * Default texture ResourceLocation in "domain:path" format.
     * T2 reuses the existing cargoRocket.png from Galacticraft.
     * T1, T3-T8 require new textures placed in assets/gtnhrocketanim/textures/model/.
     * Override per-tier in gtnhrocketanim.cfg (tierX_texturePath).
     */
    public final String defaultTexturePath;

    /**
     * Dimension IDs this tier is allowed to travel to.
     * null means no restriction (all dimensions accessible).
     * Configurable in gtnhrocketanim.cfg as tierX_allowedDimensions (comma-separated).
     */
    public final int[] defaultAllowedDimensions;

    // ------------------------------------------------------------------
    //  Constructor
    // ------------------------------------------------------------------

    CargoRocketTier(int slotsCount, int fuelCapacity, int fuelTickInterval,
                    double baseSpeed, double accelFactor,
                    double maxAscentSpeed, double maxDescentSpeed,
                    String defaultTexturePath, int[] defaultAllowedDimensions) {
        this.slotsCount              = slotsCount;
        this.fuelCapacity            = fuelCapacity;
        this.fuelTickInterval        = fuelTickInterval;
        this.baseSpeed               = baseSpeed;
        this.accelFactor             = accelFactor;
        this.maxAscentSpeed          = maxAscentSpeed;
        this.maxDescentSpeed         = maxDescentSpeed;
        this.defaultTexturePath      = defaultTexturePath;
        this.defaultAllowedDimensions = defaultAllowedDimensions;
    }

    // ------------------------------------------------------------------
    //  Fuel fluid names
    // ------------------------------------------------------------------

    /**
     * Returns the registered Forge fluid name required for this tier.
     * Values must match what GalaxySpace / Galacticraft register.
     * Override per-tier in gtnhrocketanim.cfg (tierX_fuelFluid).
     */
    public String getDefaultRequiredFuelFluid() {
        switch (this.ordinal()) {
            case 0: case 1: case 2: return "fuel";           // GC Rocket Fuel (T1-T3)
            case 3:                 return "denseHydrazine"; // Dense Hydrazine (T4)
            case 4: case 5:         return "purpleRocketFuel"; // CN3H7O3 (T5-T6)
            default:                return "greenRocketFuel";  // H8N4C2O4 (T7-T8)
        }
    }

    // ------------------------------------------------------------------
    //  Factory methods
    // ------------------------------------------------------------------

    /**
     * Maps an IRocketType.EnumRocketType ordinal (from EntityCargoRocket.rocketType)
     * to a CargoRocketTier.  This covers T1-T4 with the existing GC item system:
     *   0 (DEFAULT)    -> T1,   1 (INVENTORY27) -> T2,
     *   2 (INVENTORY36)-> T3,   3 (INVENTORY54) -> T4
     * Falls back to T2 for any unrecognised ordinal.
     */
    public static CargoRocketTier fromRocketTypeOrdinal(int ordinal) {
        switch (ordinal) {
            case 0: return T1;
            case 1: return T2;
            case 2: return T3;
            case 3: return T4;
            default: return T2;
        }
    }

    /**
     * Maps a "GTNHCargoTier" NBT integer (0-7) directly to a tier.
     * Used for T5-T8 rockets that require a custom NBT tag.
     * Falls back to T2 for absent / out-of-range values.
     */
    public static CargoRocketTier fromNbtIndex(int nbtIndex) {
        if (nbtIndex >= 0 && nbtIndex < values().length) {
            return values()[nbtIndex];
        }
        return T2;
    }
}

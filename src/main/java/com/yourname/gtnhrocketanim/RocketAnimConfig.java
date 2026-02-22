package com.yourname.gtnhrocketanim;

import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Configuration for the rocket landing/takeoff animation mod.
 * All values are loaded from config/gtnhrocketanim.cfg
 *
 * Sections:
 *   [landing]        - global landing physics
 *   [takeoff]        - global takeoff physics
 *   [particles]      - particle effects
 *   [debug]          - debug logging
 *   [tier1_cargo] .. [tier8_cargo] - per-tier overrides
 */
public final class RocketAnimConfig {

    // ========== LANDING CONFIG ==========

    /** How high above the landing pad the rocket spawns when arriving. */
    public static int landingSpawnHeight = 450;

    /** How many ticks the landing easing curve uses. */
    public static int landingEaseTicks = 120;

    /** Maximum downward speed in blocks/tick during landing (at high altitude). */
    public static double maxDescentSpeed = 0.8;

    /** Minimum downward speed in blocks/tick during landing (prevents stalling near pad). */
    public static double minDescentSpeed = 0.03;

    /** How strongly the rocket corrects toward pad center during landing (blocks/tick). */
    public static double horizontalCorrection = 0.05;

    /** When within this distance of the pad, snap exactly onto it (prevents jitter). */
    public static double snapDistance = 0.5;

    // ========== TAKEOFF CONFIG ==========

    /** Ticks to ramp from takeoffMinMultiplier to 1.0. Set to 0 to disable takeoff easing. */
    public static int takeoffRampTicks = 100;

    /** Initial thrust multiplier at the start of launch (0..1). Creates a "spool up" effect. */
    public static double takeoffMinMultiplier = 0.05;

    /** Maximum ascent speed in blocks/tick at end of ramp (before dimension transition). */
    public static double maxAscentSpeed = 8.0;

    /** Y-level altitude at which the rocket teleports to destination. */
    public static int takeoffAltitudeThreshold = 350;

    // ========== PARTICLE CONFIG ==========

    /** Enable retrograde burn particles during landing descent. */
    public static boolean enableRetrogradeBurn = true;

    /** Enable touchdown dust cloud particles. */
    public static boolean enableTouchdownParticles = true;

    /** Enable takeoff exhaust particles. */
    public static boolean enableTakeoffParticles = true;

    /** Particle intensity multiplier (0.0 to 2.0). */
    public static double particleIntensity = 1.0;

    // ========== DEBUG CONFIG ==========

    /** Enable debug logging to console. */
    public static boolean debugLogging = false;

    // ========== PER-TIER ARRAYS (index = CargoRocketTier.ordinal()) ==========

    private static final int TIER_COUNT = CargoRocketTier.values().length; // 8

    /** Inventory slot counts per tier. */
    public static int[] tierSlotsCount;

    /** Raw fuel tank capacity (mB, before rocketFuelFactor) per tier. */
    public static int[] tierFuelCapacity;

    /** Ticks between fuel drain events per tier. */
    public static int[] tierFuelTickInterval;

    /** Takeoff base-speed coefficient per tier. */
    public static double[] tierBaseSpeed;

    /** Takeoff acceleration factor per tier. */
    public static double[] tierAccelFactor;

    /** Takeoff max speed cap (blocks/tick) per tier. */
    public static double[] tierMaxAscentSpeed;

    /** Landing max descent speed (blocks/tick) per tier. */
    public static double[] tierMaxDescentSpeed;

    /** Texture ResourceLocation string ("domain:path") per tier. */
    public static String[] tierTexturePath;

    /** Required fuel fluid name per tier (Forge fluid registry name). */
    public static String[] tierFuelFluid;

    /** Allowed dimension IDs per tier (null = all allowed).  Loaded from comma-separated config. */
    public static int[][] tierAllowedDimensions;

    private RocketAnimConfig() {}

    // ------------------------------------------------------------------

    public static void load(FMLPreInitializationEvent event) {
        File cfgFile = new File(event.getModConfigurationDirectory(), "gtnhrocketanim.cfg");
        Configuration cfg = new Configuration(cfgFile);

        try {
            cfg.load();

            // ---- Landing ----
            cfg.addCustomCategoryComment("landing",
                "Settings that control how the rocket descends to the landing pad.");

            landingSpawnHeight = cfg.getInt(
                "landingSpawnHeight", "landing", landingSpawnHeight, 16, 512,
                "How high above the landing pad the rocket spawns when arriving.");

            landingEaseTicks = cfg.getInt(
                "landingEaseTicks", "landing", landingEaseTicks, 10, 600,
                "How many ticks the landing easing curve uses.");

            maxDescentSpeed = cfg.get("landing", "maxDescentSpeed", maxDescentSpeed,
                "Maximum downward speed in blocks/tick (at high altitude).").getDouble(maxDescentSpeed);

            minDescentSpeed = cfg.get("landing", "minDescentSpeed", minDescentSpeed,
                "Minimum downward speed in blocks/tick (prevents stalling near pad).").getDouble(minDescentSpeed);

            horizontalCorrection = cfg.get("landing", "horizontalCorrection", horizontalCorrection,
                "Rocket centering strength toward pad center (blocks/tick).").getDouble(horizontalCorrection);

            snapDistance = cfg.get("landing", "snapDistance", snapDistance,
                "Within this distance from the pad, snap exactly onto it.").getDouble(snapDistance);

            // ---- Takeoff ----
            cfg.addCustomCategoryComment("takeoff",
                "Settings that control the rocket takeoff animation.");

            takeoffRampTicks = cfg.getInt(
                "takeoffRampTicks", "takeoff", takeoffRampTicks, 0, 300,
                "Ticks to ramp from takeoffMinMultiplier to 1.0.  0 disables easing.");

            takeoffMinMultiplier = cfg.get("takeoff", "takeoffMinMultiplier", takeoffMinMultiplier,
                "Initial thrust multiplier (0..1).").getDouble(takeoffMinMultiplier);

            maxAscentSpeed = cfg.get("takeoff", "maxAscentSpeed", maxAscentSpeed,
                "Maximum ascent speed in blocks/tick.").getDouble(maxAscentSpeed);

            takeoffAltitudeThreshold = cfg.getInt(
                "takeoffAltitudeThreshold", "takeoff", takeoffAltitudeThreshold, 200, 500,
                "Y-level at which the rocket teleports to its destination.");

            // ---- Particles ----
            cfg.addCustomCategoryComment("particles",
                "Settings for visual particle effects.");

            enableRetrogradeBurn = cfg.getBoolean(
                "enableRetrogradeBurn", "particles", enableRetrogradeBurn,
                "Flame/smoke particles during landing descent.");

            enableTouchdownParticles = cfg.getBoolean(
                "enableTouchdownParticles", "particles", enableTouchdownParticles,
                "Dust cloud particles when rocket lands.");

            enableTakeoffParticles = cfg.getBoolean(
                "enableTakeoffParticles", "particles", enableTakeoffParticles,
                "Exhaust particles during takeoff.");

            particleIntensity = cfg.get("particles", "particleIntensity", particleIntensity,
                "Particle count multiplier (0.0 to 2.0).").getDouble(particleIntensity);

            // ---- Debug ----
            cfg.addCustomCategoryComment("debug", "Debug options for troubleshooting.");

            debugLogging = cfg.getBoolean(
                "debugLogging", "debug", debugLogging,
                "Enable verbose debug logging to console.");

            // ---- Per-tier config ----
            loadTierConfig(cfg);

        } finally {
            if (cfg.hasChanged()) cfg.save();
        }

        System.out.println("[GTNH Rocket Anim] Config loaded: landingHeight=" + landingSpawnHeight +
                          ", maxDescent=" + maxDescentSpeed + ", takeoffRamp=" + takeoffRampTicks +
                          ", tiers=" + TIER_COUNT);
    }

    // ------------------------------------------------------------------
    //  Per-tier config loading
    // ------------------------------------------------------------------

    private static void loadTierConfig(Configuration cfg) {
        CargoRocketTier[] tiers = CargoRocketTier.values();

        tierSlotsCount       = new int[TIER_COUNT];
        tierFuelCapacity     = new int[TIER_COUNT];
        tierFuelTickInterval = new int[TIER_COUNT];
        tierBaseSpeed        = new double[TIER_COUNT];
        tierAccelFactor      = new double[TIER_COUNT];
        tierMaxAscentSpeed   = new double[TIER_COUNT];
        tierMaxDescentSpeed  = new double[TIER_COUNT];
        tierTexturePath      = new String[TIER_COUNT];
        tierFuelFluid        = new String[TIER_COUNT];
        tierAllowedDimensions = new int[TIER_COUNT][];

        for (CargoRocketTier tier : tiers) {
            int i   = tier.ordinal();
            int num = i + 1; // human-readable tier number (1-8)
            String cat = "tier" + num + "_cargo";

            cfg.addCustomCategoryComment(cat,
                "Properties for Tier-" + num + " cargo rockets.\n" +
                "Leave these at defaults unless you want to override the built-in values.");

            tierSlotsCount[i] = cfg.getInt("slotsCount", cat,
                tier.slotsCount, 1, 512,
                "Inventory slot count for Tier-" + num + " cargo rockets.");

            tierFuelCapacity[i] = cfg.getInt("fuelCapacity", cat,
                tier.fuelCapacity, 100, 50000,
                "Raw fuel tank capacity in mB (before rocketFuelFactor multiplier).");

            tierFuelTickInterval[i] = cfg.getInt("fuelTickInterval", cat,
                tier.fuelTickInterval, 1, 20,
                "Ticks between each 1-unit fuel drain event.  Lower = faster consumption.");

            tierBaseSpeed[i] = cfg.get(cat, "baseSpeed",
                tier.baseSpeed,
                "Starting speed coefficient for takeoff animation.").getDouble(tier.baseSpeed);

            tierAccelFactor[i] = cfg.get(cat, "accelFactor",
                tier.accelFactor,
                "Acceleration steepness for takeoff animation.").getDouble(tier.accelFactor);

            tierMaxAscentSpeed[i] = cfg.get(cat, "maxAscentSpeed",
                tier.maxAscentSpeed,
                "Hard cap on takeoff speed (blocks/tick).").getDouble(tier.maxAscentSpeed);

            tierMaxDescentSpeed[i] = cfg.get(cat, "maxDescentSpeed",
                tier.maxDescentSpeed,
                "Maximum landing descent speed (blocks/tick).").getDouble(tier.maxDescentSpeed);

            tierTexturePath[i] = cfg.getString("texturePath", cat,
                tier.defaultTexturePath,
                "Texture ResourceLocation: \"domain:path/to/texture.png\".");

            tierFuelFluid[i] = cfg.getString("fuelFluid", cat,
                tier.getDefaultRequiredFuelFluid(),
                "Forge fluid name accepted by this tier (e.g. fuel, denseHydrazine).");

            // Allowed dimensions: stored as comma-separated integers in config
            String dimDefault = dimsToString(tier.defaultAllowedDimensions);
            String dimCfg = cfg.getString("allowedDimensions", cat,
                dimDefault,
                "Comma-separated dimension IDs this tier can reach.\n" +
                "Leave empty or omit to allow all dimensions.");

            tierAllowedDimensions[i] = parseDims(dimCfg);
        }
    }

    // ------------------------------------------------------------------
    //  Typed accessors
    // ------------------------------------------------------------------

    public static int getSlotsCount(CargoRocketTier tier) {
        return tierSlotsCount != null ? tierSlotsCount[tier.ordinal()] : tier.slotsCount;
    }

    public static int getFuelCapacity(CargoRocketTier tier) {
        return tierFuelCapacity != null ? tierFuelCapacity[tier.ordinal()] : tier.fuelCapacity;
    }

    public static int getFuelTickInterval(CargoRocketTier tier) {
        return tierFuelTickInterval != null ? tierFuelTickInterval[tier.ordinal()] : tier.fuelTickInterval;
    }

    public static double getTierBaseSpeed(CargoRocketTier tier) {
        return tierBaseSpeed != null ? tierBaseSpeed[tier.ordinal()] : tier.baseSpeed;
    }

    public static double getTierAccelFactor(CargoRocketTier tier) {
        return tierAccelFactor != null ? tierAccelFactor[tier.ordinal()] : tier.accelFactor;
    }

    public static double getTierMaxAscentSpeed(CargoRocketTier tier) {
        return tierMaxAscentSpeed != null ? tierMaxAscentSpeed[tier.ordinal()] : tier.maxAscentSpeed;
    }

    public static double getTierMaxDescentSpeed(CargoRocketTier tier) {
        return tierMaxDescentSpeed != null ? tierMaxDescentSpeed[tier.ordinal()] : tier.maxDescentSpeed;
    }

    public static String getTexturePath(CargoRocketTier tier) {
        return tierTexturePath != null ? tierTexturePath[tier.ordinal()] : tier.defaultTexturePath;
    }

    public static String getFuelFluid(CargoRocketTier tier) {
        return tierFuelFluid != null ? tierFuelFluid[tier.ordinal()] : tier.getDefaultRequiredFuelFluid();
    }

    /**
     * Returns the allowed dimension array for this tier, or null if there are no restrictions.
     */
    public static int[] getAllowedDimensions(CargoRocketTier tier) {
        if (tierAllowedDimensions == null) return tier.defaultAllowedDimensions;
        return tierAllowedDimensions[tier.ordinal()];
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static String dimsToString(int[] dims) {
        if (dims == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dims.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(dims[i]);
        }
        return sb.toString();
    }

    private static int[] parseDims(String s) {
        if (s == null || s.trim().isEmpty()) return null; // null = no restriction
        String[] parts = s.split(",");
        int[] dims = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    dims[count++] = Integer.parseInt(trimmed);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (count == 0) return null;
        if (count < dims.length) {
            int[] trimmed = new int[count];
            System.arraycopy(dims, 0, trimmed, 0, count);
            return trimmed;
        }
        return dims;
    }
}

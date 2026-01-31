package com.yourname.gtnhrocketanim;

import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Configuration for the rocket landing/takeoff animation mod.
 * All values are loaded from config/gtnhrocketanim.cfg
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
    public static boolean debugLogging = false;  // Default false for production

    private RocketAnimConfig() {}

    public static void load(FMLPreInitializationEvent event) {
        File cfgFile = new File(event.getModConfigurationDirectory(), "gtnhrocketanim.cfg");
        Configuration cfg = new Configuration(cfgFile);

        try {
            cfg.load();

            // Landing settings
            cfg.addCustomCategoryComment("landing", 
                "Settings that control how the rocket descends to the landing pad.\n" +
                "The rocket will spawn at landingSpawnHeight blocks above the pad and smoothly descend.");
            
            landingSpawnHeight = cfg.getInt(
                "landingSpawnHeight", "landing",
                landingSpawnHeight, 16, 512,
                "How high above the landing pad the rocket spawns when arriving.\n" +
                "Lower values = faster arrival but less dramatic. Higher = more cinematic landing.\n" +
                "Default: 120"
            );

            landingEaseTicks = cfg.getInt(
                "landingEaseTicks", "landing",
                landingEaseTicks, 10, 600,
                "How many ticks the landing easing curve uses (for reference/tuning).\n" +
                "Default: 120"
            );

            maxDescentSpeed = cfg.get("landing", "maxDescentSpeed", maxDescentSpeed,
                "Maximum downward speed in blocks/tick during landing (at high altitude).\n" +
                "1.0 = 20 blocks/second. Default: 0.8").getDouble(maxDescentSpeed);

            minDescentSpeed = cfg.get("landing", "minDescentSpeed", minDescentSpeed,
                "Minimum downward speed in blocks/tick during landing.\n" +
                "Prevents the rocket from stalling near the pad. Default: 0.03").getDouble(minDescentSpeed);

            horizontalCorrection = cfg.get("landing", "horizontalCorrection", horizontalCorrection,
                "How strongly the rocket corrects toward pad center during landing (blocks/tick).\n" +
                "Higher values = snappier centering. Default: 0.05").getDouble(horizontalCorrection);

            snapDistance = cfg.get("landing", "snapDistance", snapDistance,
                "When within this distance of the pad, snap exactly onto it.\n" +
                "Prevents jitter at the end of landing. Default: 0.25").getDouble(snapDistance);

            // Takeoff settings
            cfg.addCustomCategoryComment("takeoff", 
                "Settings that control the rocket takeoff animation.\n" +
                "Creates a 'spool up' effect where thrust builds gradually.");
            
            takeoffRampTicks = cfg.getInt(
                "takeoffRampTicks", "takeoff",
                takeoffRampTicks, 0, 300,
                "Ticks to ramp from takeoffMinMultiplier to 1.0.\n" +
                "Set to 0 to disable takeoff easing entirely.\n" +
                "Default: 100 (5 seconds)"
            );

            takeoffMinMultiplier = cfg.get("takeoff", "takeoffMinMultiplier", takeoffMinMultiplier,
                "Initial thrust multiplier at the start of launch (0..1).\n" +
                "Lower = slower initial rise, more dramatic spool-up.\n" +
                "Default: 0.05").getDouble(takeoffMinMultiplier);

            maxAscentSpeed = cfg.get("takeoff", "maxAscentSpeed", maxAscentSpeed,
                "Maximum ascent speed in blocks/tick at end of takeoff ramp.\n" +
                "Higher = rocket accelerates to faster speed. 2.0 = 40 blocks/second.\n" +
                "Default: 2.0").getDouble(maxAscentSpeed);

            takeoffAltitudeThreshold = cfg.getInt(
                "takeoffAltitudeThreshold", "takeoff",
                takeoffAltitudeThreshold, 200, 500,
                "Y-level altitude at which the rocket teleports to destination.\n" +
                "Once the rocket reaches this height, it disappears and reappears above the destination pad.\n" +
                "Default: 350"
            );

            // Particle settings
            cfg.addCustomCategoryComment("particles",
                "Settings for visual particle effects.\n" +
                "All particles are server-synced and visible to all players.");
            
            enableRetrogradeBurn = cfg.getBoolean(
                "enableRetrogradeBurn", "particles",
                enableRetrogradeBurn,
                "Enable flame/smoke particles during landing descent (retrograde burn).\n" +
                "Default: true"
            );
            
            enableTouchdownParticles = cfg.getBoolean(
                "enableTouchdownParticles", "particles",
                enableTouchdownParticles,
                "Enable dust cloud particles when rocket lands.\n" +
                "Default: true"
            );
            
            enableTakeoffParticles = cfg.getBoolean(
                "enableTakeoffParticles", "particles",
                enableTakeoffParticles,
                "Enable exhaust particles during takeoff.\n" +
                "Default: true"
            );
            
            particleIntensity = cfg.get("particles", "particleIntensity", particleIntensity,
                "Multiplier for particle count (0.0 to 2.0).\n" +
                "Lower for better performance, higher for more dramatic effects.\n" +
                "Default: 1.0").getDouble(particleIntensity);

            // Debug settings
            cfg.addCustomCategoryComment("debug", 
                "Debug options for troubleshooting.");
            
            debugLogging = cfg.getBoolean(
                "debugLogging", "debug",
                debugLogging,
                "Enable debug logging to console. Useful for troubleshooting.\n" +
                "Default: false"
            );

        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
        
        System.out.println("[GTNH Rocket Anim] Config loaded: landingHeight=" + landingSpawnHeight + 
                          ", maxDescent=" + maxDescentSpeed + ", takeoffRamp=" + takeoffRampTicks +
                          ", particles=" + (enableRetrogradeBurn || enableTouchdownParticles || enableTakeoffParticles));
    }
}

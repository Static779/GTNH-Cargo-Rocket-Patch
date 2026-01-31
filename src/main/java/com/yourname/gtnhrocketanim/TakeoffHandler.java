package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Handles the takeoff animation logic for cargo rockets.
 * Uses cubic acceleration: speed = k * t³
 */
public final class TakeoffHandler {
    
    private TakeoffHandler() {}
    
    /**
     * Process one tick of takeoff animation.
     * @return true if animation is still in progress, false if teleport was triggered
     */
    public static boolean processTick(Entity rocket, World w, int launchPhase) {
        int entityId = RocketStateTracker.id(rocket);
        
        // Get or initialize takeoff tracking
        Long startTick = RocketStateTracker.getTakeoffStartTick(entityId);
        Double startY = RocketStateTracker.getTakeoffStartY(entityId);
        
        if (startTick == null || startY == null) {
            // Initialize if somehow missing
            System.out.println("[GTNH Rocket Anim] TAKEOFF: EntityID=" + entityId + 
                             " - startTick/startY was NULL! " + RocketStateTracker.getDebugInfo(entityId));
            startTick = w.getTotalWorldTime();
            startY = rocket.posY;
            RocketStateTracker.setTakeoffStartTick(entityId, startTick);
            RocketStateTracker.setTakeoffStartY(entityId, startY);
        }
        
        long ticksInTakeoff = w.getTotalWorldTime() - startTick;
        
        // Log takeoff progress every 20 ticks
        if (ticksInTakeoff % 20 == 0) {
            System.out.println("[GTNH Rocket Anim] Takeoff: tick=" + ticksInTakeoff + 
                             ", posY=" + String.format("%.1f", rocket.posY) +
                             "/" + RocketAnimConfig.takeoffAltitudeThreshold +
                             ", motionY=" + String.format("%.3f", rocket.motionY));
        }
        
        // === EXPONENTIAL ROCKET ACCELERATION ===
        // Cubic curve: speed = k * t³
        double ascentSpeed = calculateAscentSpeed(ticksInTakeoff);
        
        // Apply motion
        rocket.motionY = ascentSpeed;
        rocket.posY += ascentSpeed; // Direct position update required for GC cargo rockets
        
        // Spawn takeoff particles (engine exhaust)
        RocketParticles.spawnTakeoff(w, rocket, launchPhase, ticksInTakeoff);
        
        // Check if we've reached the altitude threshold
        if (rocket.posY >= RocketAnimConfig.takeoffAltitudeThreshold) {
            triggerTeleport(rocket, entityId);
            return false; // Animation complete
        }
        
        return true; // Animation still in progress
    }
    
    /**
     * Calculate ascent speed using pure quadratic acceleration.
     * Formula: speed = k * t²
     * Starts at essentially 0 and smoothly builds - like a real rocket.
     * Using higher k for faster initial acceleration feel.
     */
    private static double calculateAscentSpeed(long ticksInTakeoff) {
        double k = 0.00012D;  // Higher coefficient for snappier acceleration
        double t = (double) ticksInTakeoff;
        double ascentSpeed = k * t * t;
        
        // Tiny minimum just to show initial movement (0.01 = 0.2 blocks/sec)
        if (ascentSpeed < 0.01D && ticksInTakeoff > 0) {
            ascentSpeed = 0.01D;
        }
        
        return ascentSpeed;
    }
    
    /**
     * Trigger the teleport to the destination.
     */
    private static void triggerTeleport(Entity rocket, int entityId) {
        int[] pendingDest = RocketStateTracker.getPendingDestination(entityId);
        Integer freq = RocketStateTracker.getPendingFrequency(entityId);
        if (freq == null) freq = 0;
        
        System.out.println("[GTNH Rocket Anim] Takeoff complete! Reached Y=" + 
                         String.format("%.1f", rocket.posY) + 
                         " (threshold: " + RocketAnimConfig.takeoffAltitudeThreshold +
                         "). Teleporting to destination: (" + 
                         pendingDest[0] + ", " + pendingDest[1] + ", " + pendingDest[2] + ")");
        
        // Calculate arrival height
        int arrivalHeight = RocketAnimConfig.landingSpawnHeight;
        
        // Set landing flag using reflection
        setLandingFlag(rocket, true);
        
        // Zero motion before teleport
        rocket.motionX = 0;
        rocket.motionY = 0;
        rocket.motionZ = 0;
        
        // Teleport to destination
        double teleportX = pendingDest[0] + 0.5D;
        double teleportY = pendingDest[1] + arrivalHeight + (freq == 1 ? 0 : 1);
        double teleportZ = pendingDest[2] + 0.5D;
        
        rocket.setPosition(teleportX, teleportY, teleportZ);
        
        System.out.println("[GTNH Rocket Anim] Teleported to (" + 
                         String.format("%.1f", teleportX) + ", " + 
                         String.format("%.1f", teleportY) + ", " + 
                         String.format("%.1f", teleportZ) + ")");
        
        // Clear all takeoff data - landing animation will take over
        RocketStateTracker.clearAllTakeoffData(entityId);
    }
    
    /**
     * Set the landing flag on the rocket using reflection.
     */
    private static void setLandingFlag(Entity rocket, boolean value) {
        try {
            java.lang.reflect.Field landingField = rocket.getClass().getField("landing");
            landingField.setBoolean(rocket, value);
        } catch (Exception e) {
            // Try superclass
            try {
                java.lang.reflect.Field landingField = rocket.getClass().getSuperclass().getField("landing");
                landingField.setBoolean(rocket, value);
            } catch (Exception e2) {
                System.out.println("[GTNH Rocket Anim] Failed to set landing flag: " + e2);
            }
        }
    }
}

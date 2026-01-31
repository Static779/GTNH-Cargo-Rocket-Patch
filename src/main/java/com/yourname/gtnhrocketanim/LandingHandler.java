package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Handles the landing animation logic for cargo rockets.
 * Uses physics-based descent with acceleration.
 */
public final class LandingHandler {
    
    private LandingHandler() {}
    
    /**
     * Process one tick of landing animation.
     * @return true if animation is still in progress, false if landed
     */
    public static boolean processTick(Entity rocket, World w, int targetX, int targetY, int targetZ, boolean isServer) {
        int entityId = RocketStateTracker.id(rocket);
        
        // Initialize landing tracking
        Long startTick = RocketStateTracker.getLandingStartTick(entityId);
        if (startTick == null && isServer) {
            startTick = w.getTotalWorldTime();
            RocketStateTracker.setLandingStartTick(entityId, startTick);
            RocketStateTracker.setLandingVelocity(entityId, 0.0D);
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Rocket entering landing mode at Y=" + rocket.posY);
            }
        }
        
        final double padX = targetX + 0.5D;
        final double padZ = targetZ + 0.5D;
        final double padY = targetY + 1.0D;

        final double dx = padX - rocket.posX;
        final double dz = padZ - rocket.posZ;
        final double dy = rocket.posY - padY;

        // Check if we're at snap distance (close to landing)
        boolean atSnapDistance = Math.abs(dx) < RocketAnimConfig.snapDistance &&
                                 Math.abs(dz) < RocketAnimConfig.snapDistance &&
                                 dy >= 0 && dy < RocketAnimConfig.snapDistance;
        
        // Only modify physics on server
        if (isServer) {
            // Snap when close enough to prevent jitter
            if (atSnapDistance) {
                rocket.setPosition(padX, padY, padZ);
                rocket.motionX = 0;
                rocket.motionY = 0;
                rocket.motionZ = 0;
                RocketStateTracker.clearLandingState(entityId);
                
                // Touchdown particles - server-side dust cloud
                RocketParticles.spawnTouchdown(w, padX, padY, padZ);
                
                if (RocketAnimConfig.debugLogging) {
                    System.out.println("[GTNH Rocket Anim] Rocket landed successfully");
                }
                return false; // Landed
            }

            // Horizontal correction with exponential smoothing
            double hFactor = 0.05D;
            double h = RocketAnimConfig.horizontalCorrection;
            rocket.motionX = clamp(dx * hFactor, -h, h);
            rocket.motionZ = clamp(dz * hFactor, -h, h);

            // === RETROGRADE LANDING: Fast approach, slow touchdown ===
            // Speed is proportional to distance from pad
            // Uses square root for smooth deceleration curve
            double maxDescentSpeed = RocketAnimConfig.maxDescentSpeed;
            double minDescentSpeed = RocketAnimConfig.minDescentSpeed;
            
            // Distance-based speed: sqrt curve for natural deceleration
            // At 100 blocks: full speed, at 1 block: minimum speed
            double heightFactor = Math.sqrt(dy / 100.0D);
            if (heightFactor > 1.0D) heightFactor = 1.0D;
            if (heightFactor < 0.0D) heightFactor = 0.0D;
            
            // Interpolate between min and max speed based on height
            double descentSpeed = minDescentSpeed + (maxDescentSpeed - minDescentSpeed) * heightFactor;
            
            // Apply velocity (negative because descending)
            rocket.motionY = -descentSpeed;
        }
        
        // Spawn retrograde burn particles (runs on CLIENT side, checked inside method)
        if (dy > 0.5D) {
            RocketParticles.spawnRetrogradeBurn(w, rocket, dy);
        }
        
        return true; // Still descending
    }
    
    /**
     * Clear landing state when not landing.
     */
    public static void clearState(int entityId) {
        RocketStateTracker.clearLandingState(entityId);
    }
    
    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

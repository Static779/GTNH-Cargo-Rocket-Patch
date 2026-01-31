package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Entry point methods called by ASM-injected hooks.
 * 
 * TAKEOFF ANIMATION APPROACH (v3 - BLOCK COMPLETELY):
 * 
 * Problem: GC calls onReachAtmosphere() every tick. If we let ANY call through,
 * it immediately teleports the rocket and sets landing=true.
 * 
 * Solution: ALWAYS block onReachAtmosphere() until altitude threshold is reached.
 * In our tick hook, we manually call setTarget() to get destination info,
 * then manually drive the rocket upward.
 * 
 * When altitude >= threshold:
 *   - Stop blocking onReachAtmosphere()
 *   - GC will call it and do the actual teleport
 * 
 * For LANDING: We intercept moveToDestination to apply our custom spawn height,
 * then LandingHandler applies smooth descent physics.
 */
public final class RocketAnimHooks {

    // Counter for throttled debug logging
    private static int tickCounter = 0;
    
    private RocketAnimHooks() {}

    /**
     * Called at the START of moveToDestination to intercept teleportation.
     * 
     * We ONLY use this for landing height modification now.
     * Takeoff blocking is handled by shouldDelayAtmosphereTransition.
     * 
     * Returns: modified height to use, or -1 to abort teleport
     */
    public static int interceptMoveToDestination(Entity rocket, Object targetVecObj, int frequency, int originalHeight) {
        if (rocket.worldObj == null || rocket.worldObj.isRemote) {
            return originalHeight;
        }
        
        int entityId = RocketStateTracker.id(rocket);
        double currentY = rocket.posY;
        
        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] moveToDestination called: originalHeight=" + originalHeight +
                              ", frequency=" + frequency + ", Y=" + String.format("%.1f", currentY));
        }

        // For ANY call, apply our landing spawn height if appropriate
        if (originalHeight >= 100) {
            // This is a takeoff/transfer call - apply landing height
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Applying spawn height: " + originalHeight + " -> " + RocketAnimConfig.landingSpawnHeight);
            }
            return RocketAnimConfig.landingSpawnHeight;
        }

        // Height 0 or small means direct landing
        if (originalHeight > 0 && originalHeight < 100) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Post-transfer landing, applying spawn height: " +
                                  originalHeight + " -> " + RocketAnimConfig.landingSpawnHeight);
            }
            return RocketAnimConfig.landingSpawnHeight;
        }
        
        return originalHeight;
    }

    /**
     * Used by our moveToDestination patch: treat "800" as "arrival height" and replace with config.
     * LEGACY - now interceptMoveToDestination handles the main logic.
     */
    public static int resolveArrivalHeight(int arg) {
        if (arg >= 100) {
            return RocketAnimConfig.landingSpawnHeight;
        }
        return arg;
    }

    /**
     * Called at the START of onReachAtmosphere() to check if we should delay.
     * 
     * NEW APPROACH (v3): ALWAYS block until altitude is reached.
     * We don't let ANY calls through - we manually call setTarget() from our tick hook.
     * 
     * Returns true to BLOCK the method, false to ALLOW.
     */
    public static boolean shouldDelayAtmosphereTransition(Entity rocket) {
        if (rocket.worldObj == null || rocket.worldObj.isRemote) {
            return false;
        }
        
        int entityId = RocketStateTracker.id(rocket);
        double currentY = rocket.posY;
        double threshold = RocketAnimConfig.takeoffAltitudeThreshold;
        
        // If Y is NaN, something is already broken - don't interfere
        if (Double.isNaN(currentY)) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Y is NaN, allowing onReachAtmosphere");
            }
            return false;
        }

        // Check if we're below threshold - BLOCK
        if (currentY < threshold) {
            // Mark that we've started takeoff
            if (!RocketStateTracker.hasTargetInitialized(entityId)) {
                RocketStateTracker.setTargetInitialized(entityId, true);
                RocketStateTracker.setTakeoffStartY(entityId, currentY);
                if (RocketAnimConfig.debugLogging) {
                    System.out.println("[GTNH Rocket Anim] === BLOCKING onReachAtmosphere === " +
                                      "Starting takeoff animation at Y=" + String.format("%.1f", currentY) +
                                      ", threshold=" + threshold);
                }
            }

            // Throttled logging
            if (RocketAnimConfig.debugLogging && tickCounter % 40 == 0) {
                System.out.println("[GTNH Rocket Anim] Blocking onReachAtmosphere - Y=" +
                                  String.format("%.1f", currentY) + " < " + threshold);
            }
            return true; // BLOCK
        }

        // THRESHOLD REACHED: Allow the method to run (will do the actual teleport)
        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Altitude threshold reached at Y=" +
                              String.format("%.1f", currentY) + " >= " + threshold + " - allowing teleport!");
        }
        RocketStateTracker.clearTakeoffState(entityId);
        return false; // ALLOW
    }

    /**
     * Apply landing easing and TAKEOFF DRIVING.
     * Injected near the end of EntityCargoRocket.func_70071_h_() (tick).
     * 
     * For TAKEOFF: GC's physics don't work properly when we block onReachAtmosphere().
     * So we MANUALLY DRIVE the rocket upward by setting posY and motionY ourselves.
     * 
     * We also spawn particles to make the takeoff look nice.
     */
    public static void onCargoRocketTick(Entity rocket, boolean landing, Object targetVecObj, int launchPhase, float timeSinceLaunch) {
        try {
            tickCounter++;
            
            World w = rocket.worldObj;
            if (w == null) return;
            
            boolean isServer = !w.isRemote;
            int entityId = RocketStateTracker.id(rocket);
            double currentY = rocket.posY;
            
            // Debug logging every 100 ticks
            if (RocketAnimConfig.debugLogging && tickCounter % 100 == 1) {
                // Also check destinationFrequency
                int destFreq = -1;
                try {
                    java.lang.reflect.Field destField = rocket.getClass().getField("destinationFrequency");
                    destFreq = destField.getInt(rocket);
                } catch (Exception ignored) {}

                System.out.println("[GTNH Rocket Anim] === TICK #" + tickCounter + " === EntityID=" + entityId +
                                   ", server=" + isServer +
                                   ", landing=" + landing +
                                   ", launchPhase=" + launchPhase +
                                   ", timeSinceLaunch=" + timeSinceLaunch +
                                   ", destFreq=" + destFreq +
                                   ", targetVec=" + (targetVecObj != null ? "present" : "null") +
                                   ", Y=" + String.format("%.1f", currentY) +
                                   ", motionY=" + String.format("%.3f", rocket.motionY));
            }

            // ===== TAKEOFF IN PROGRESS =====
            // We know takeoff is happening if:
            // 1. launchPhase == 2 (LAUNCHED)
            // 2. !landing (not in landing mode yet)
            // 3. Y < threshold and Y is valid
            if (!landing && launchPhase == 2 && !Double.isNaN(currentY)) {
                double threshold = RocketAnimConfig.takeoffAltitudeThreshold;
                
                if (currentY < threshold) {
                    // Track takeoff start
                    Double startY = RocketStateTracker.getTakeoffStartY(entityId);
                    if (startY == null && isServer) {
                        startY = currentY;
                        RocketStateTracker.setTakeoffStartY(entityId, startY);
                        if (RocketAnimConfig.debugLogging) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF DETECTED at Y=" + String.format("%.1f", startY) +
                                              " - QUADRATIC ACCELERATION ENGAGED");
                        }
                    }
                    
                    // === SERVER: Drive the rocket upward with ACCELERATING curve ===
                    if (isServer) {
                        if (startY == null) startY = currentY;
                        
                        // Distance to travel
                        double totalDistance = threshold - startY;
                        double traveled = currentY - startY;
                        double remaining = threshold - currentY;
                        
                        // Progress: 0.0 at start, 1.0 at threshold
                        double progress = traveled / totalDistance;
                        if (progress < 0) progress = 0;
                        // No upper cap - let it keep accelerating even past threshold
                        
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
                        
                        // Apply motion
                        rocket.motionY = upwardSpeed;
                        rocket.posY += upwardSpeed;
                        
                        // Ensure the entity syncs to clients
                        rocket.velocityChanged = true;
                        
                        if (RocketAnimConfig.debugLogging && tickCounter % 20 == 0) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF PROGRESS: Y=" + String.format("%.1f", rocket.posY) +
                                              ", speed=" + String.format("%.2f", upwardSpeed) +
                                              ", progress=" + String.format("%.1f%%", progress * 100) +
                                              ", remaining=" + String.format("%.1f", remaining));
                        }
                    }
                    
                    // === CLIENT: Spawn particles ===
                    if (!isServer) {
                        Double savedStartY = RocketStateTracker.getTakeoffStartY(entityId);
                        if (savedStartY == null) {
                            // Client may not have server's start Y, use current as approximation
                            savedStartY = currentY - (threshold - currentY) * 0.1; // rough estimate
                            RocketStateTracker.setTakeoffStartY(entityId, savedStartY);
                        }
                        double traveled = currentY - savedStartY;
                        RocketParticles.spawnTakeoff(w, rocket, launchPhase, (long)(traveled * 2));
                    }
                    return;
                }
                
                // Threshold reached - clear tracking (server-side)
                if (isServer && currentY >= threshold) {
                    Double savedStartY = RocketStateTracker.getTakeoffStartY(entityId);
                    if (savedStartY != null) {
                        if (RocketAnimConfig.debugLogging) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF COMPLETE at Y=" + String.format("%.1f", currentY) +
                                              " - rocket will teleport on next tick");
                        }
                        RocketStateTracker.clearTakeoffState(entityId);
                    }
                }
            }

            // ===== LANDING =====
            if (landing && targetVecObj != null) {
                // Clear takeoff tracking when we start landing
                RocketStateTracker.clearTakeoffTracking(entityId);
                
                // Extract target coordinates
                int targetX, targetY, targetZ;
                try {
                    java.lang.reflect.Field fx = targetVecObj.getClass().getField("x");
                    java.lang.reflect.Field fy = targetVecObj.getClass().getField("y");
                    java.lang.reflect.Field fz = targetVecObj.getClass().getField("z");
                    targetX = fx.getInt(targetVecObj);
                    targetY = fy.getInt(targetVecObj);
                    targetZ = fz.getInt(targetVecObj);
                } catch (Exception e) {
                    if (RocketAnimConfig.debugLogging) {
                        System.out.println("[GTNH Rocket Anim] Failed to read targetVec: " + e);
                    }
                    return;
                }
                
                LandingHandler.processTick(rocket, w, targetX, targetY, targetZ, isServer);
                return;
            } else {
                LandingHandler.clearState(entityId);
            }

            // ===== IDLE (not launched, not landing) =====
            if (launchPhase == 0) {
                // Reset all tracking when rocket is idle
                RocketStateTracker.clearAllTakeoffData(entityId);
            }
            
        } catch (Throwable t) {
            System.out.println("[GTNH Rocket Anim] ERROR in tick hook: " + t.getMessage());
            t.printStackTrace();
        }
    }
}

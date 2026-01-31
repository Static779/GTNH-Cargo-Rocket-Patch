package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Entry point methods called by ASM-injected hooks.
 * 
 * Delegates to:
 * - RocketStateTracker: State management for landing/takeoff
 * - TakeoffHandler: Takeoff animation logic
 * - LandingHandler: Landing animation logic
 * - RocketParticles: Particle effects
 *
 * Notes:
 * - Server authoritative: only alter motion/position on server (world.isRemote == false)
 * - Client can read state but should not modify shared HashMaps during active animations
 */
public final class RocketAnimHooks {

    // Counter for throttled debug logging
    private static int tickCounter = 0;
    
    private RocketAnimHooks() {}

    /**
     * Called at the START of moveToDestination to intercept the teleport.
     * Instead of teleporting immediately, we store the destination and return
     * a special value that tells the ASM-patched method to abort.
     * 
     * When this returns -1, the moveToDestination method should RETURN without teleporting.
     * The tick hook will handle the takeoff animation and trigger teleport when ready.
     */
    public static int interceptMoveToDestination(Entity rocket, Object targetVecObj, int frequency, int originalHeight) {
        if (rocket.worldObj == null || rocket.worldObj.isRemote) {
            // Client side - let it proceed normally for sync
            return originalHeight;
        }
        
        int entityId = RocketStateTracker.id(rocket);
        
        // Check if we already have a pending destination (takeoff in progress)
        if (RocketStateTracker.hasPendingDestination(entityId)) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] moveToDestination called again - takeoff already in progress, aborting");
            }
            return -1; // Signal to abort
        }
        
        // Extract coordinates from BlockVec3
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
                System.out.println("[GTNH Rocket Anim] Failed to read targetVec in intercept: " + e);
            }
            return originalHeight; // Let it proceed normally on error
        }
        
        // Store the destination for the tick hook to use later
        RocketStateTracker.setPendingDestination(entityId, new int[] { targetX, targetY, targetZ });
        RocketStateTracker.setPendingFrequency(entityId, frequency);
        RocketStateTracker.setTakeoffStartY(entityId, rocket.posY);
        RocketStateTracker.setTakeoffStartTick(entityId, rocket.worldObj.getTotalWorldTime());
        
        System.out.println("[GTNH Rocket Anim] Intercepted moveToDestination! EntityID=" + entityId + 
                          ", Destination: (" + targetX + ", " + targetY + ", " + targetZ + 
                          "), starting takeoff animation from Y=" + String.format("%.1f", rocket.posY) +
                          ", startTick=" + rocket.worldObj.getTotalWorldTime());
        
        // Return -1 to signal the patched method to abort without teleporting
        return -1;
    }

    /**
     * Used by our moveToDestination patch: treat "800" as "arrival height" and replace with config.
     * LEGACY - now interceptMoveToDestination handles the main logic.
     */
    public static int resolveArrivalHeight(int arg) {
        if (arg >= 100) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Arrival height resolved: " + arg + " -> " + RocketAnimConfig.landingSpawnHeight);
            }
            return RocketAnimConfig.landingSpawnHeight;
        }
        return arg;
    }

    /**
     * Called at the START of onReachAtmosphere() to check if we should delay the transition.
     * DISABLED for now - letting natural position counteraction handle the visual delay instead.
     */
    public static boolean shouldDelayAtmosphereTransition(Entity rocket) {
        return false;
    }

    /**
     * Apply landing + takeoff easing. Injected near the end of EntityCargoRocket.func_70071_h_() (tick).
     */
    public static void onCargoRocketTick(Entity rocket, boolean landing, Object targetVecObj, int launchPhase, float timeSinceLaunch) {
        try {
            tickCounter++;
            
            World w = rocket.worldObj;
            if (w == null) return;
            
            boolean isServer = !w.isRemote;
            int entityId = RocketStateTracker.id(rocket);
            
            // Check if we have a pending takeoff destination
            int[] pendingDest = RocketStateTracker.getPendingDestination(entityId);
            boolean inTakeoffAnimation = (pendingDest != null);
            
            // Debug logging every 100 ticks
            if (tickCounter % 100 == 1) {
                System.out.println("[GTNH Rocket Anim] === TICK #" + tickCounter + " === EntityID=" + entityId +
                                   ", server=" + isServer +
                                   ", landing=" + landing + 
                                   ", inTakeoff=" + inTakeoffAnimation +
                                   ", launchPhase=" + launchPhase + 
                                   ", timeSinceLaunch=" + timeSinceLaunch +
                                   ", targetVec=" + (targetVecObj != null ? "present" : "null") +
                                   ", Y=" + String.format("%.1f", rocket.posY) +
                                   ", motionY=" + String.format("%.3f", rocket.motionY));
            }

            // ===== TAKEOFF ANIMATION =====
            if (inTakeoffAnimation) {
                if (isServer) {
                    // Server handles physics
                    TakeoffHandler.processTick(rocket, w, launchPhase);
                } else {
                    // Client handles particles only
                    Long startTick = RocketStateTracker.getTakeoffStartTick(entityId);
                    long ticksInTakeoff = startTick != null ? (w.getTotalWorldTime() - startTick) : 0;
                    RocketParticles.spawnTakeoff(w, rocket, launchPhase, ticksInTakeoff);
                }
                return; // Don't process landing logic during takeoff
            }

            // ===== LANDING =====
            if (landing && targetVecObj != null) {
                // Only clear takeoff tracking if NOT in takeoff animation
                // (Client can reach here while server is doing takeoff animation)
                if (!inTakeoffAnimation) {
                    RocketStateTracker.clearTakeoffTracking(entityId);
                }
                
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

            // ===== FALLBACK TAKEOFF LOGIC (if intercept didn't work) =====
            if (!landing && launchPhase >= 1 && isServer && !inTakeoffAnimation) {
                // Track takeoff for particles (fallback only)
                Long startTick = RocketStateTracker.getTakeoffStartTick(entityId);
                if (startTick == null) {
                    RocketStateTracker.setTakeoffStartTick(entityId, w.getTotalWorldTime());
                }
            } else if (!landing && launchPhase == 0 && !inTakeoffAnimation) {
                // Reset tracking when not launched (but NOT during takeoff animation!)
                RocketStateTracker.clearAllTakeoffData(entityId);
            }
            
        } catch (Throwable t) {
            System.out.println("[GTNH Rocket Anim] ERROR in tick hook: " + t.getMessage());
            t.printStackTrace();
        }
    }
}

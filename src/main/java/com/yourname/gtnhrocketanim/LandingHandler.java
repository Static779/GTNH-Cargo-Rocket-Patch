package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Handles the landing animation logic for cargo rockets.
 * Uses physics-based descent with deceleration.
 * Max descent speed scales with the rocket's CargoRocketTier.
 */
public final class LandingHandler {

    private LandingHandler() {}

    /**
     * Process one tick of landing animation.
     * @param tier the resolved tier of this cargo rocket (controls descent speed)
     * @return true if animation is still in progress, false if landed
     */
    public static boolean processTick(Entity rocket, World w,
                                      int targetX, int targetY, int targetZ,
                                      boolean isServer, CargoRocketTier tier) {
        int entityId = RocketStateTracker.id(rocket);

        // Initialize landing tracking
        Long startTick = RocketStateTracker.getLandingStartTick(entityId);
        if (startTick == null && isServer) {
            startTick = w.getTotalWorldTime();
            RocketStateTracker.setLandingStartTick(entityId, startTick);
            RocketStateTracker.setLandingVelocity(entityId, 0.0D);
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Rocket entering landing mode at Y=" +
                                   rocket.posY + " tier=" + tier.name());
            }
        }

        final double padX = targetX + 0.5D;
        final double padZ = targetZ + 0.5D;
        final double padY = targetY + 1.0D;

        final double dx = padX - rocket.posX;
        final double dz = padZ - rocket.posZ;
        final double dy = rocket.posY - padY;

        boolean atSnapDistance = Math.abs(dx) < RocketAnimConfig.snapDistance &&
                                 Math.abs(dz) < RocketAnimConfig.snapDistance &&
                                 dy >= 0 && dy < RocketAnimConfig.snapDistance;

        if (isServer) {
            if (atSnapDistance) {
                rocket.setPosition(padX, padY, padZ);
                rocket.motionX = 0;
                rocket.motionY = 0;
                rocket.motionZ = 0;
                RocketStateTracker.clearLandingState(entityId);

                RocketParticles.spawnTouchdown(w, padX, padY, padZ);

                if (RocketAnimConfig.debugLogging) {
                    System.out.println("[GTNH Rocket Anim] Rocket landed successfully (tier=" + tier.name() + ")");
                }
                return false; // Landed
            }

            // Horizontal correction with exponential smoothing
            double hFactor = 0.05D;
            double h = RocketAnimConfig.horizontalCorrection;
            rocket.motionX = clamp(dx * hFactor, -h, h);
            rocket.motionZ = clamp(dz * hFactor, -h, h);

            // Tier-aware descent speed from config
            double maxDescentSpeed = RocketAnimConfig.getTierMaxDescentSpeed(tier);
            double minDescentSpeed = RocketAnimConfig.minDescentSpeed;

            // Square-root deceleration: fast at height, slow near pad
            double heightFactor = Math.sqrt(dy / 100.0D);
            if (heightFactor > 1.0D) heightFactor = 1.0D;
            if (heightFactor < 0.0D) heightFactor = 0.0D;

            double descentSpeed = minDescentSpeed + (maxDescentSpeed - minDescentSpeed) * heightFactor;
            rocket.motionY = -descentSpeed;
        }

        // Retrograde burn particles (client-side)
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

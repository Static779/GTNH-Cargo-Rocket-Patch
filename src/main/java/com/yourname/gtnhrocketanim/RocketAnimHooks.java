package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
 *
 * TIERED CARGO ROCKETS:
 * Each cargo rocket entity carries a "GTNHCargoTier" NBT tag (int 0-7).
 * For T1-T4 this is derived from the rocketType ordinal automatically.
 * The tier controls fuel capacity, inventory size, animation speed, and texture.
 */
public final class RocketAnimHooks {

    // Counter for throttled debug logging
    private static int tickCounter = 0;

    private RocketAnimHooks() {}

    // ==========================================================================
    //  REFLECTION SETUP
    //  All Galacticraft fields are accessed via reflection since GC is not a
    //  compile-time dependency.  Forge types (FluidTank, NBT, etc.) are direct.
    // ==========================================================================

    private static Field  rocketTypeField    = null; // EntityCargoRocket.rocketType
    private static Field  entityFuelTankField = null; // EntitySpaceshipBase.fuelTank
    private static Field  fluidTankCapField   = null; // FluidTank.capacity (Forge)
    private static Method fluidTankDrainMethod = null; // FluidTank.drain(int, boolean)
    private static boolean reflectionInitialized = false;

    /**
     * Lazily initialise all reflection handles.
     * Safe to call repeatedly; work is only done once.
     */
    private static void ensureReflectionReady() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            Class<?> cargoClass = Class.forName(
                "micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket");
            rocketTypeField = cargoClass.getDeclaredField("rocketType");
            rocketTypeField.setAccessible(true);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: Could not reflect rocketType field: " + e);
        }

        try {
            Class<?> spaceshipBase = Class.forName(
                "micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase");
            entityFuelTankField = spaceshipBase.getDeclaredField("fuelTank");
            entityFuelTankField.setAccessible(true);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: Could not reflect fuelTank field: " + e);
        }

        // FluidTank.capacity field name — try common names
        for (String name : new String[]{ "capacity", "tankCapacity" }) {
            try {
                fluidTankCapField = FluidTank.class.getDeclaredField(name);
                fluidTankCapField.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (fluidTankCapField == null) {
            System.out.println("[GTNH Rocket Anim] WARN: Could not find FluidTank capacity field. " +
                               "Fuel-tank resizing will be disabled.");
        }

        try {
            fluidTankDrainMethod = FluidTank.class.getMethod("drain", int.class, boolean.class);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: Could not reflect FluidTank.drain: " + e);
        }
    }

    // ==========================================================================
    //  TIER UTILITY
    // ==========================================================================

    /**
     * Reads the IRocketType.EnumRocketType ordinal from an EntityCargoRocket.
     * Returns -1 on failure (e.g. during construction before rocketType is set).
     */
    public static int getRocketTypeOrdinal(Object entity) {
        ensureReflectionReady();
        if (rocketTypeField == null) return -1;
        try {
            Object enumVal = rocketTypeField.get(entity);
            if (enumVal == null) return -1;
            return ((Enum<?>) enumVal).ordinal();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns the CargoRocketTier for a given entity.
     *
     * Priority:
     *   1. Cached value in RocketStateTracker (set by hookReadNbt or hookPostConstructorTierInit)
     *   2. rocketType ordinal (covers T1-T4 without any extra NBT)
     *   3. T2 fallback
     */
    public static CargoRocketTier getCargoTierFromEntity(Entity entity) {
        int entityId = RocketStateTracker.id(entity);
        CargoRocketTier cached = RocketStateTracker.getCargoTier(entityId);
        // If the default T2 is returned, it might just be uninitialised — derive from entity
        if (cached == CargoRocketTier.T2) {
            int ordinal = getRocketTypeOrdinal(entity);
            if (ordinal >= 0) {
                CargoRocketTier derived = CargoRocketTier.fromRocketTypeOrdinal(ordinal);
                RocketStateTracker.setCargoTier(entityId, derived);
                return derived;
            }
        }
        return cached;
    }

    // ==========================================================================
    //  ASM HOOKS — called from patched EntityCargoRocket methods
    // ==========================================================================

    /**
     * ASM HOOK — replaces the body of EntityCargoRocket.getFuelTankCapacity().
     *
     * Called during construction BEFORE rocketType is set, so we return the
     * maximum possible capacity to ensure the FluidTank can hold any tier's fuel.
     * hookPostConstructorTierInit() will resize it down afterwards.
     */
    public static int hookGetFuelTankCapacity(Object entity) {
        int ordinal = getRocketTypeOrdinal(entity);
        if (ordinal < 0) {
            return CargoRocketTier.T8.fuelCapacity; // safe max during construction
        }
        CargoRocketTier tier = CargoRocketTier.fromRocketTypeOrdinal(ordinal);
        return RocketAnimConfig.getFuelCapacity(tier);
    }

    /**
     * ASM HOOK — injected into the 5-arg EntityCargoRocket constructor,
     * immediately after the PUTFIELD rocketType instruction.
     *
     * Resizes the FluidTank to the correct capacity for the tier.
     * Must happen here because getFuelTankCapacity() is called during super()
     * before rocketType is available.
     */
    public static void hookPostConstructorTierInit(Object entity) {
        ensureReflectionReady();
        int ordinal = getRocketTypeOrdinal(entity);
        if (ordinal < 0) return;

        CargoRocketTier tier = CargoRocketTier.fromRocketTypeOrdinal(ordinal);
        RocketStateTracker.setCargoTier(RocketStateTracker.id((Entity) entity), tier);

        resizeFuelTank(entity, tier);
    }

    /**
     * ASM HOOK — injected at the end of EntityCargoRocket.readEntityFromNBT().
     *
     * Reads "GTNHCargoTier" NBT key (for T5-T8) and caches the tier.
     * Also ensures the FluidTank capacity is correct after NBT load.
     */
    public static void hookReadNbt(Entity entity, NBTTagCompound nbt) {
        ensureReflectionReady();

        CargoRocketTier tier;
        if (nbt.hasKey("GTNHCargoTier")) {
            // Explicit override (T5-T8 or manual)
            int nbtIndex = nbt.getInteger("GTNHCargoTier");
            tier = CargoRocketTier.fromNbtIndex(nbtIndex);
        } else {
            // Derive from rocketType ordinal (covers T1-T4 automatically)
            int ordinal = getRocketTypeOrdinal(entity);
            tier = (ordinal >= 0) ? CargoRocketTier.fromRocketTypeOrdinal(ordinal) : CargoRocketTier.T2;
        }

        RocketStateTracker.setCargoTier(RocketStateTracker.id(entity), tier);
        resizeFuelTank(entity, tier);

        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] hookReadNbt: entity=" + entity.getEntityId() +
                               " tier=" + tier.name() + " slots=" + RocketAnimConfig.getSlotsCount(tier));
        }
    }

    /**
     * ASM HOOK — injected at the end of EntityCargoRocket.writeEntityToNBT().
     *
     * Writes "GTNHCargoTier" so that T5-T8 tiers survive save/load cycles.
     * Harmless for T1-T4 (they can always be derived from rocketType).
     */
    public static void hookWriteNbt(Entity entity, NBTTagCompound nbt) {
        CargoRocketTier tier = RocketStateTracker.getCargoTier(RocketStateTracker.id(entity));
        nbt.setInteger("GTNHCargoTier", tier.ordinal());
    }

    /**
     * ASM HOOK — replaces the body of EntityCargoRocket.getSizeInventory().
     */
    public static int hookGetSizeInventory(Object entity) {
        int ordinal = getRocketTypeOrdinal(entity);
        if (ordinal < 0) return 0;
        // Try the cache first; fall back to ordinal mapping
        CargoRocketTier tier = RocketStateTracker.getCargoTier(RocketStateTracker.id((Entity) entity));
        if (tier == CargoRocketTier.T2 && ordinal != 1) {
            // Cache might be stale — re-derive
            tier = CargoRocketTier.fromRocketTypeOrdinal(ordinal);
        }
        return RocketAnimConfig.getSlotsCount(tier);
    }

    /**
     * ASM HOOK — replaces the GETSTATIC cargoRocketTexture in RenderCargoRocket.
     * Returns the tier-appropriate ResourceLocation from a per-tier cache.
     */
    private static final ResourceLocation[] TEXTURE_CACHE = new ResourceLocation[CargoRocketTier.values().length];

    public static ResourceLocation hookGetCargoRocketTexture(Object entity) {
        // entity here is the EntityCargoRocket passed as first param to renderBuggy
        CargoRocketTier tier = CargoRocketTier.T2; // default
        try {
            int ordinal = getRocketTypeOrdinal(entity);
            if (ordinal >= 0) {
                // Attempt cache lookup by entity id
                int entityId = ((Entity) entity).getEntityId();
                CargoRocketTier cached = RocketStateTracker.getCargoTier(entityId);
                tier = (cached != CargoRocketTier.T2 || ordinal == 1) ? cached
                       : CargoRocketTier.fromRocketTypeOrdinal(ordinal);
            }
        } catch (Exception ignored) {}

        int idx = tier.ordinal();
        if (TEXTURE_CACHE[idx] == null) {
            TEXTURE_CACHE[idx] = buildResourceLocation(RocketAnimConfig.getTexturePath(tier));
        }
        return TEXTURE_CACHE[idx];
    }

    private static ResourceLocation buildResourceLocation(String path) {
        int colon = path.indexOf(':');
        if (colon < 0) return new ResourceLocation(path);
        return new ResourceLocation(path.substring(0, colon), path.substring(colon + 1));
    }

    // ==========================================================================
    //  FUEL TANK RESIZING (shared helper)
    // ==========================================================================

    private static void resizeFuelTank(Object entity, CargoRocketTier tier) {
        if (entityFuelTankField == null || fluidTankCapField == null) return;
        try {
            FluidTank tank = (FluidTank) entityFuelTankField.get(entity);
            if (tank == null) return;

            int fuelFactor = getGCFuelFactor();
            int newCapacity = RocketAnimConfig.getFuelCapacity(tier) * fuelFactor;

            fluidTankCapField.setInt(tank, newCapacity);

            // Trim excess fluid so the tank state is consistent
            int currentAmount = tank.getFluidAmount();
            if (currentAmount > newCapacity && fluidTankDrainMethod != null) {
                fluidTankDrainMethod.invoke(tank, currentAmount - newCapacity, true);
            }
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: fuel tank resize failed: " + e);
        }
    }

    /** Returns ConfigManagerCore.rocketFuelFactor via reflection, defaulting to 1. */
    private static int getGCFuelFactor() {
        try {
            Class<?> cfg = Class.forName("micdoodle8.mods.galacticraft.core.ConfigManagerCore");
            Field f = cfg.getField("rocketFuelFactor");
            return f.getInt(null);
        } catch (Exception e) {
            return 1;
        }
    }

    // ==========================================================================
    //  EXISTING HOOKS (unchanged API, updated to be tier-aware where needed)
    // ==========================================================================

    /**
     * Called at the START of moveToDestination to intercept teleportation.
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

        if (originalHeight >= 100) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Applying spawn height: " + originalHeight +
                                   " -> " + RocketAnimConfig.landingSpawnHeight);
            }
            return RocketAnimConfig.landingSpawnHeight;
        }

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
     * Used by our moveToDestination patch.  LEGACY - interceptMoveToDestination handles
     * the main logic now.
     */
    public static int resolveArrivalHeight(int arg) {
        if (arg >= 100) {
            return RocketAnimConfig.landingSpawnHeight;
        }
        return arg;
    }

    /**
     * Called at the START of onReachAtmosphere() to check if we should delay.
     * Returns true to BLOCK the method, false to ALLOW.
     */
    public static boolean shouldDelayAtmosphereTransition(Entity rocket) {
        if (rocket.worldObj == null || rocket.worldObj.isRemote) {
            return false;
        }

        int entityId = RocketStateTracker.id(rocket);
        double currentY = rocket.posY;
        double threshold = RocketAnimConfig.takeoffAltitudeThreshold;

        if (Double.isNaN(currentY)) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Y is NaN, allowing onReachAtmosphere");
            }
            return false;
        }

        if (currentY < threshold) {
            if (!RocketStateTracker.hasTargetInitialized(entityId)) {
                RocketStateTracker.setTargetInitialized(entityId, true);
                RocketStateTracker.setTakeoffStartY(entityId, currentY);
                if (RocketAnimConfig.debugLogging) {
                    System.out.println("[GTNH Rocket Anim] === BLOCKING onReachAtmosphere === " +
                                      "Starting takeoff animation at Y=" + String.format("%.1f", currentY) +
                                      ", threshold=" + threshold);
                }
            }

            if (RocketAnimConfig.debugLogging && tickCounter % 40 == 0) {
                System.out.println("[GTNH Rocket Anim] Blocking onReachAtmosphere - Y=" +
                                  String.format("%.1f", currentY) + " < " + threshold);
            }
            return true;
        }

        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Altitude threshold reached at Y=" +
                              String.format("%.1f", currentY) + " >= " + threshold + " - allowing teleport!");
        }
        RocketStateTracker.clearTakeoffState(entityId);
        return false;
    }

    /**
     * Apply landing easing and TAKEOFF DRIVING.
     * Injected near the end of EntityCargoRocket.func_70071_h_() (tick).
     *
     * Takeoff physics now scale with the rocket's CargoRocketTier.
     */
    public static void onCargoRocketTick(Entity rocket, boolean landing, Object targetVecObj,
                                          int launchPhase, float timeSinceLaunch) {
        try {
            tickCounter++;

            World w = rocket.worldObj;
            if (w == null) return;

            boolean isServer = !w.isRemote;
            int entityId = RocketStateTracker.id(rocket);
            double currentY = rocket.posY;

            // Resolve the tier for this entity
            CargoRocketTier tier = getCargoTierFromEntity(rocket);

            // Debug logging every 100 ticks
            if (RocketAnimConfig.debugLogging && tickCounter % 100 == 1) {
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
                                   ", motionY=" + String.format("%.3f", rocket.motionY) +
                                   ", tier=" + tier.name());
            }

            // ===== TAKEOFF IN PROGRESS =====
            if (!landing && launchPhase == 2 && !Double.isNaN(currentY)) {
                double threshold = RocketAnimConfig.takeoffAltitudeThreshold;

                if (currentY < threshold) {
                    Double startY = RocketStateTracker.getTakeoffStartY(entityId);
                    if (startY == null && isServer) {
                        startY = currentY;
                        RocketStateTracker.setTakeoffStartY(entityId, startY);
                        if (RocketAnimConfig.debugLogging) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF DETECTED at Y=" +
                                               String.format("%.1f", startY) +
                                               " tier=" + tier.name() + " - ACCELERATION ENGAGED");
                        }
                    }

                    // === SERVER: Drive the rocket upward with tier-scaled acceleration ===
                    if (isServer) {
                        if (startY == null) startY = currentY;

                        double totalDistance = threshold - startY;
                        double traveled      = currentY - startY;

                        double progress = (totalDistance > 0) ? traveled / totalDistance : 0;
                        if (progress < 0) progress = 0;

                        // Tier-specific parameters from config
                        double baseSpeed    = RocketAnimConfig.getTierBaseSpeed(tier);
                        double accelFactor  = RocketAnimConfig.getTierAccelFactor(tier);
                        double maxSpeed     = RocketAnimConfig.getTierMaxAscentSpeed(tier);

                        // Quadratic acceleration: (1 + progress * accelFactor)^2
                        double speedMultiplier = Math.pow(1.0 + progress * accelFactor, 2.0);

                        // Time-based additive shift (scaled by tier's base speed)
                        double additiveShift = (timeSinceLaunch / 20.0) * baseSpeed * 6.25;

                        // Engine spool-up ramp (2 seconds)
                        double launchRamp = Math.min(timeSinceLaunch / 40.0, 1.0);

                        double upwardSpeed = (baseSpeed * speedMultiplier + additiveShift) * launchRamp;

                        // Hard cap at tier max
                        if (upwardSpeed > maxSpeed) upwardSpeed = maxSpeed;

                        rocket.motionY = upwardSpeed;
                        rocket.posY   += upwardSpeed;
                        rocket.velocityChanged = true;

                        if (RocketAnimConfig.debugLogging && tickCounter % 20 == 0) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF: tier=" + tier.name() +
                                               " Y=" + String.format("%.1f", rocket.posY) +
                                               " speed=" + String.format("%.2f", upwardSpeed) +
                                               "/" + String.format("%.1f", maxSpeed) +
                                               " progress=" + String.format("%.1f%%", progress * 100));
                        }
                    }

                    // === CLIENT: Spawn particles ===
                    if (!isServer) {
                        Double savedStartY = RocketStateTracker.getTakeoffStartY(entityId);
                        if (savedStartY == null) {
                            savedStartY = currentY - (threshold - currentY) * 0.1;
                            RocketStateTracker.setTakeoffStartY(entityId, savedStartY);
                        }
                        double traveled = currentY - savedStartY;
                        RocketParticles.spawnTakeoff(w, rocket, launchPhase, (long)(traveled * 2));
                    }
                    return;
                }

                // Threshold reached
                if (isServer && currentY >= threshold) {
                    Double savedStartY = RocketStateTracker.getTakeoffStartY(entityId);
                    if (savedStartY != null) {
                        if (RocketAnimConfig.debugLogging) {
                            System.out.println("[GTNH Rocket Anim] TAKEOFF COMPLETE at Y=" +
                                               String.format("%.1f", currentY) +
                                               " tier=" + tier.name() + " - rocket will teleport");
                        }
                        RocketStateTracker.clearTakeoffState(entityId);
                    }
                }
            }

            // ===== LANDING =====
            if (landing && targetVecObj != null) {
                RocketStateTracker.clearTakeoffTracking(entityId);

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

                LandingHandler.processTick(rocket, w, targetX, targetY, targetZ, isServer, tier);
                return;
            } else {
                LandingHandler.clearState(entityId);
            }

            // ===== IDLE =====
            if (launchPhase == 0) {
                RocketStateTracker.clearAllTakeoffData(entityId);
            }

        } catch (Throwable t) {
            System.out.println("[GTNH Rocket Anim] ERROR in tick hook: " + t.getMessage());
            t.printStackTrace();
        }
    }
}

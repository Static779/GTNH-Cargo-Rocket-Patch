package com.yourname.gtnhrocketanim;

import java.util.HashMap;
import net.minecraft.entity.Entity;

/**
 * Tracks state for rocket landing and takeoff animations.
 * Uses entity IDs as keys to avoid holding Entity references.
 */
public final class RocketStateTracker {

    // ========== LANDING STATE ==========
    private static final HashMap<Integer, Long>    landingStartTicks = new HashMap<>();
    private static final HashMap<Integer, Double>  landingVelocity   = new HashMap<>();

    // ========== TAKEOFF STATE ==========
    private static final HashMap<Integer, Long>    takeoffStartTicks = new HashMap<>();
    private static final HashMap<Integer, Double>  takeoffStartY     = new HashMap<>();
    private static final HashMap<Integer, Boolean> takeoffLogged     = new HashMap<>();

    // ========== PENDING DESTINATION (for intercepted moveToDestination) ==========
    private static final HashMap<Integer, int[]>   pendingDestination = new HashMap<>();
    private static final HashMap<Integer, Integer> pendingFrequency   = new HashMap<>();

    // ========== ATMOSPHERE DELAY ==========
    private static final HashMap<Integer, Long> atmosphereDelayStart = new HashMap<>();

    // ========== CARGO TIER CACHE ==========
    /** Caches the resolved CargoRocketTier for each entity, keyed by entity ID.
     *  Populated by hookReadNbt and on first access in getCargoTier(). */
    private static final HashMap<Integer, CargoRocketTier> cargoTierCache = new HashMap<>();

    private RocketStateTracker() {}

    // ----- Helper -----
    public static int id(Entity e) {
        return e.getEntityId();
    }

    // ========== LANDING METHODS ==========

    public static Long getLandingStartTick(int entityId) {
        return landingStartTicks.get(entityId);
    }

    public static void setLandingStartTick(int entityId, long tick) {
        landingStartTicks.put(entityId, tick);
    }

    public static void clearLandingStartTick(int entityId) {
        landingStartTicks.remove(entityId);
    }

    public static Double getLandingVelocity(int entityId) {
        return landingVelocity.get(entityId);
    }

    public static void setLandingVelocity(int entityId, double velocity) {
        landingVelocity.put(entityId, velocity);
    }

    public static void clearLandingVelocity(int entityId) {
        landingVelocity.remove(entityId);
    }

    public static void clearLandingState(int entityId) {
        landingStartTicks.remove(entityId);
        landingVelocity.remove(entityId);
    }

    // ========== TAKEOFF METHODS ==========

    public static Long getTakeoffStartTick(int entityId) {
        return takeoffStartTicks.get(entityId);
    }

    public static void setTakeoffStartTick(int entityId, long tick) {
        takeoffStartTicks.put(entityId, tick);
    }

    public static boolean hasTakeoffStartTick(int entityId) {
        return takeoffStartTicks.containsKey(entityId);
    }

    public static Double getTakeoffStartY(int entityId) {
        return takeoffStartY.get(entityId);
    }

    public static void setTakeoffStartY(int entityId, double y) {
        takeoffStartY.put(entityId, y);
    }

    public static boolean hasTakeoffStartY(int entityId) {
        return takeoffStartY.containsKey(entityId);
    }

    public static Boolean getTakeoffLogged(int entityId) {
        return takeoffLogged.get(entityId);
    }

    public static void setTakeoffLogged(int entityId, boolean logged) {
        takeoffLogged.put(entityId, logged);
    }

    /**
     * Returns true once takeoff has been initialised for this entity (i.e. once
     * setTakeoffStartY has been called).  Used by shouldDelayAtmosphereTransition
     * to avoid double-initialisation.
     */
    public static boolean hasTargetInitialized(int entityId) {
        return takeoffStartY.containsKey(entityId);
    }

    /**
     * No-op – "initialised" state is implicitly tracked by the presence of a
     * takeoffStartY entry.  Exists only so that shouldDelayAtmosphereTransition
     * can call it without compile errors.
     */
    public static void setTargetInitialized(int entityId, boolean value) {
        // intentional no-op: hasTakeoffStartY() is the authoritative check
    }

    public static void clearTakeoffState(int entityId) {
        takeoffStartTicks.remove(entityId);
        takeoffStartY.remove(entityId);
        takeoffLogged.remove(entityId);
    }

    public static void clearTakeoffTracking(int entityId) {
        takeoffStartTicks.remove(entityId);
        takeoffLogged.remove(entityId);
    }

    // ========== PENDING DESTINATION METHODS ==========

    public static int[] getPendingDestination(int entityId) {
        return pendingDestination.get(entityId);
    }

    public static void setPendingDestination(int entityId, int[] coords) {
        pendingDestination.put(entityId, coords);
    }

    public static boolean hasPendingDestination(int entityId) {
        return pendingDestination.containsKey(entityId);
    }

    public static Integer getPendingFrequency(int entityId) {
        return pendingFrequency.get(entityId);
    }

    public static void setPendingFrequency(int entityId, int frequency) {
        pendingFrequency.put(entityId, frequency);
    }

    public static void clearPendingDestination(int entityId) {
        pendingDestination.remove(entityId);
        pendingFrequency.remove(entityId);
    }

    public static void clearAllTakeoffData(int entityId) {
        pendingDestination.remove(entityId);
        pendingFrequency.remove(entityId);
        takeoffStartTicks.remove(entityId);
        takeoffStartY.remove(entityId);
        takeoffLogged.remove(entityId);
    }

    // ========== ATMOSPHERE DELAY METHODS ==========

    public static Long getAtmosphereDelayStart(int entityId) {
        return atmosphereDelayStart.get(entityId);
    }

    public static void setAtmosphereDelayStart(int entityId, long tick) {
        atmosphereDelayStart.put(entityId, tick);
    }

    public static void clearAtmosphereDelayStart(int entityId) {
        atmosphereDelayStart.remove(entityId);
    }

    // ========== CARGO TIER METHODS ==========

    /**
     * Returns the cached CargoRocketTier for the given entity, or T2 as the default
     * if no tier has been set yet (e.g. a newly constructed entity before NBT is read).
     */
    public static CargoRocketTier getCargoTier(int entityId) {
        CargoRocketTier tier = cargoTierCache.get(entityId);
        return tier != null ? tier : CargoRocketTier.T2;
    }

    /**
     * Stores the resolved tier for an entity so that every tick handler can look it
     * up cheaply without repeated reflection.  Called from hookReadNbt and from the
     * post-constructor hook (hookPostConstructorTierInit).
     */
    public static void setCargoTier(int entityId, CargoRocketTier tier) {
        cargoTierCache.put(entityId, tier);
    }

    /**
     * Removes the cached tier (e.g. when the entity is removed from the world).
     */
    public static void clearCargoTier(int entityId) {
        cargoTierCache.remove(entityId);
    }

    // ========== DEBUG INFO ==========

    public static String getDebugInfo(int entityId) {
        return "pendingDest=" + pendingDestination.containsKey(entityId) +
               ", takeoffStartTicks=" + takeoffStartTicks.containsKey(entityId) +
               ", takeoffStartY=" + takeoffStartY.containsKey(entityId) +
               ", cargoTier=" + cargoTierCache.get(entityId) +
               ", map sizes: pending=" + pendingDestination.size() +
               ", ticks=" + takeoffStartTicks.size() + ", y=" + takeoffStartY.size();
    }
}

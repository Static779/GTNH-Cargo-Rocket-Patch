package com.yourname.gtnhrocketanim;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;

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
    //  PENDING SPAWN TIER
    //  Set by ItemCargoRocketTiered.onItemUse() on the server thread immediately
    //  BEFORE calling new EntityCargoRocket(...) so that:
    //    • hookGetFuelTankCapacity() returns the right capacity during super()
    //    • hookGetSizeInventory() returns the right slot count during field-init
    //    • hookPostConstructorTierInit() caches and resizes for the right tier
    //  Cleared again as soon as the constructor returns.
    // ==========================================================================

    private static final ThreadLocal<CargoRocketTier> PENDING_SPAWN_TIER = new ThreadLocal<>();

    public static void setPendingSpawnTier(CargoRocketTier tier) {
        PENDING_SPAWN_TIER.set(tier);
    }

    public static void clearPendingSpawnTier() {
        PENDING_SPAWN_TIER.remove();
    }

    // ==========================================================================
    //  REFLECTION SETUP
    //  All Galacticraft fields are accessed via reflection since GC is not a
    //  compile-time dependency.  Forge types (FluidTank, NBT, etc.) are direct.
    // ==========================================================================

    private static Field  rocketTypeField     = null; // EntityCargoRocket.rocketType
    private static Field  entityFuelTankField  = null; // EntitySpaceshipBase.fuelTank
    private static boolean reflectionInitialized = false;

    /**
     * GC's original hardcoded fuel capacity for EntityCargoRocket.
     * Matched from decompile: public int getFuelTankCapacity() { return 2000; }
     * We return this for T1/T2 so original GC rockets are completely unaffected.
     */
    private static final int GC_CARGO_FUEL_CAPACITY = 2000;

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
        // Priority 1: pending tier (set by ItemCargoRocketTiered before construction)
        CargoRocketTier pending = PENDING_SPAWN_TIER.get();
        if (pending != null) {
            if (pending == CargoRocketTier.T1 || pending == CargoRocketTier.T2)
                return GC_CARGO_FUEL_CAPACITY;
            return RocketAnimConfig.getFuelCapacity(pending);
        }

        // Priority 2: state cache (set by hookPostConstructorTierInit / hookReadNbt)
        // This correctly resolves T5-T8 whose rocketType ordinal only reaches TIER_4.
        int entityId = ((Entity) entity).getEntityId();
        CargoRocketTier cached = RocketStateTracker.getCargoTier(entityId);
        CargoRocketTier tier;
        if (cached != CargoRocketTier.T2) {
            // Cache holds a specific tier (T1, T3-T8) — use it
            tier = cached;
        } else {
            // Fall back to rocketType ordinal (construction phase, T1-T4)
            int ordinal = getRocketTypeOrdinal(entity);
            if (ordinal < 0) return CargoRocketTier.T8.fuelCapacity; // safe max
            tier = CargoRocketTier.fromRocketTypeOrdinal(ordinal);
        }

        // T1/T2: return GC's original value so native GC rockets are unaffected
        if (tier == CargoRocketTier.T1 || tier == CargoRocketTier.T2)
            return GC_CARGO_FUEL_CAPACITY;
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

        // Prefer the pending tier (set by ItemCargoRocketTiered for T5-T8)
        CargoRocketTier pending = PENDING_SPAWN_TIER.get();
        CargoRocketTier tier = (pending != null)
            ? pending
            : CargoRocketTier.fromRocketTypeOrdinal(ordinal);

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
        // Pending tier set by ItemCargoRocketTiered during construction
        CargoRocketTier pending = PENDING_SPAWN_TIER.get();
        if (pending != null) return RocketAnimConfig.getSlotsCount(pending);

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

    // ==========================================================================
    //  TIERED ROCKET MODEL RENDERING
    //  Called in place of model.renderAll() inside RenderCargoRocket.renderBuggy().
    //  We stay in the SAME GL context (translations/rotations already applied by
    //  renderBuggy) and simply swap the OBJ geometry for T3-T8.
    // ==========================================================================

    /** OBJ model objects keyed by CargoRocketTier ordinal. */
    private static final Object[] TIER_MODELS          = new Object[CargoRocketTier.values().length];
    private static volatile boolean modelsInitialized  = false;

    /**
     * Scale correction applied when rendering T3-T8 OBJ models inside renderBuggy.
     *
     * renderBuggy applies glScalef(0.4, 0.4, 0.4) before calling renderAll.
     * GC Asteroids (T3) and GalaxySpace (T4-T8) renderers both apply glScalef(0.9, 0.9, 0.9).
     * To match the intended size: 0.9 / 0.4 = 2.25
     */
    private static final double TIER_MODEL_SCALE = 2.25;

    /**
     * Replaces model.renderAll() inside renderBuggy.
     * For T1/T2: calls the original cargo model unchanged.
     * For T3-T8: applies a 2.25x scale correction then calls the tier OBJ model.
     *
     * @param defaultModel  the cargo rocket IModelCustom (already on the stack)
     * @param entity        the EntityCargoRocket being rendered (ALOAD 1 from renderBuggy)
     */
    public static void hookRenderModel(Object defaultModel, Object entity) {
        CargoRocketTier tier;
        try {
            tier = getCargoTierFromEntity((Entity) entity);
        } catch (Exception e) {
            invokeRenderAll(defaultModel);
            return;
        }

        if (tier == CargoRocketTier.T1 || tier == CargoRocketTier.T2) {
            invokeRenderAll(defaultModel);
            return;
        }

        ensureTierModels();

        Object model = TIER_MODELS[tier.ordinal()];
        if (model != null) {
            // Push a corrective scale so the GS/GCA model renders at its intended size
            glPushMatrix();
            glScaled(TIER_MODEL_SCALE);
            invokeRenderAll(model);
            glPopMatrix();
        } else {
            invokeRenderAll(defaultModel);
        }
    }

    private static void invokeRenderAll(Object model) {
        try {
            model.getClass().getMethod("renderAll").invoke(model);
        } catch (Exception e) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] renderAll failed: " + e);
            }
        }
    }

    // --- GL helpers (cached reflection — LWJGL not available on server) ---
    private static java.lang.reflect.Method GL_PUSH = null;
    private static java.lang.reflect.Method GL_POP  = null;
    private static java.lang.reflect.Method GL_SCALE = null;
    private static volatile boolean glReady = false;

    private static synchronized void ensureGL() {
        if (glReady) return;
        glReady = true;
        try {
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11");
            GL_PUSH  = gl.getMethod("glPushMatrix");
            GL_POP   = gl.getMethod("glPopMatrix");
            GL_SCALE = gl.getMethod("glScaled", double.class, double.class, double.class);
        } catch (Exception e) {
            // Expected on the server — hookRenderModel is never called server-side
        }
    }

    private static void glPushMatrix() {
        ensureGL();
        if (GL_PUSH != null) try { GL_PUSH.invoke(null); } catch (Exception ignored) {}
    }

    private static void glPopMatrix() {
        ensureGL();
        if (GL_POP != null) try { GL_POP.invoke(null); } catch (Exception ignored) {}
    }

    private static void glScaled(double f) {
        ensureGL();
        if (GL_SCALE != null) try { GL_SCALE.invoke(null, f, f, f); } catch (Exception ignored) {}
    }

    private static synchronized void ensureTierModels() {
        if (modelsInitialized) return;
        modelsInitialized = true;

        try {
            Class<?> advLoader = Class.forName("net.minecraftforge.client.model.AdvancedModelLoader");
            java.lang.reflect.Method loadModel =
                    advLoader.getMethod("loadModel", ResourceLocation.class);

            // T3: GC Asteroids
            try {
                TIER_MODELS[CargoRocketTier.T3.ordinal()] = loadModel.invoke(null,
                        new ResourceLocation("galacticraftasteroids", "models/tier3rocket.obj"));
                System.out.println("[GTNH Rocket Anim] T3 model loaded");
            } catch (Exception e) {
                System.out.println("[GTNH Rocket Anim] Could not load T3 model: " + e);
            }

            // T4-T8: GalaxySpace
            for (CargoRocketTier t : new CargoRocketTier[]{
                    CargoRocketTier.T4, CargoRocketTier.T5, CargoRocketTier.T6,
                    CargoRocketTier.T7, CargoRocketTier.T8}) {
                int n = t.ordinal() + 1;
                try {
                    TIER_MODELS[t.ordinal()] = loadModel.invoke(null,
                            new ResourceLocation("galaxyspace", "models/tier" + n + "rocket.obj"));
                    System.out.println("[GTNH Rocket Anim] T" + n + " model loaded");
                } catch (Exception e) {
                    System.out.println("[GTNH Rocket Anim] Could not load T" + n + " model: " + e);
                }
            }
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] AdvancedModelLoader not available: " + e);
        }
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
            String path = RocketAnimConfig.getTexturePath(tier);
            ResourceLocation rl = buildResourceLocation(path);
            // Verify the texture file exists; fall back to T2's texture if not.
            // This is only ever called client-side (from rendering), so Minecraft
            // class is safe to access via reflection.
            TEXTURE_CACHE[idx] = verifyTextureOrFallback(rl, tier);
        }
        return TEXTURE_CACHE[idx];
    }

    /**
     * For textures in our own mod's domain ("gtnhrocketanim:"), verify the file
     * is actually bundled in the jar by checking via the classloader.
     * If missing, returns T2's texture instead so nothing renders as a checkerboard.
     *
     * Textures in other domains (e.g. "galacticraftmars:") are assumed to exist
     * in those mods and are returned unchanged.
     *
     * This approach avoids any Minecraft class references, so it is server-safe
     * and not affected by obfuscated method names.
     */
    private static ResourceLocation verifyTextureOrFallback(ResourceLocation rl, CargoRocketTier tier) {
        if (tier == CargoRocketTier.T2) return rl; // T2 lives in galacticraftmars, always present

        // Only check textures we are responsible for providing
        if (!"gtnhrocketanim".equals(rl.getResourceDomain())) return rl;

        // Check via classloader: file must be at assets/<domain>/<path> inside our jar
        String resourcePath = "/assets/" + rl.getResourceDomain() + "/" + rl.getResourcePath();
        java.io.InputStream stream = RocketAnimHooks.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            // Texture not bundled — fall back to T2's cargo rocket texture
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] Texture not bundled for " + tier.name()
                    + " (" + rl + "), using T2 fallback. "
                    + "Add the PNG or override texturePath in config.");
            }
            return buildResourceLocation(RocketAnimConfig.getTexturePath(CargoRocketTier.T2));
        }
        try { stream.close(); } catch (java.io.IOException ignored) {}
        return rl;
    }

    private static ResourceLocation buildResourceLocation(String path) {
        int colon = path.indexOf(':');
        if (colon < 0) return new ResourceLocation(path);
        return new ResourceLocation(path.substring(0, colon), path.substring(colon + 1));
    }

    // ==========================================================================
    //  FUEL TANK RESIZING (shared helper)
    // ==========================================================================

    /**
     * Replaces the entity's FluidTank with a TieredFluidTank sized for the tier.
     *
     * The TieredFluidTank accepts any valid GC rocket fuel (testFuel logic).
     * Tier-specific fuel enforcement is done at the loader level via
     * hookFuelLoaderTierCheck(), not inside the tank.
     *
     * For T1/T2 we keep GC's original capacity (2000 × factor).
     * Any valid fuel already in the old tank is transferred to the new one.
     */
    private static void resizeFuelTank(Object entity, CargoRocketTier tier) {
        if (entityFuelTankField == null) return;
        try {
            FluidTank oldTank = (FluidTank) entityFuelTankField.get(entity);
            if (oldTank == null) return;

            int fuelFactor = getGCFuelFactor();
            int newCapacity = (tier == CargoRocketTier.T1 || tier == CargoRocketTier.T2)
                    ? GC_CARGO_FUEL_CAPACITY * fuelFactor
                    : RocketAnimConfig.getFuelCapacity(tier) * fuelFactor;

            // Skip if already a correctly-sized TieredFluidTank
            if (oldTank instanceof TieredFluidTank && oldTank.getCapacity() == newCapacity) {
                return;
            }

            TieredFluidTank newTank = new TieredFluidTank(newCapacity);

            // Transfer any valid fuel already in the old tank
            FluidStack existing = oldTank.getFluid();
            if (existing != null && existing.getFluid() != null) {
                int amount = Math.min(existing.amount, newCapacity);
                newTank.fill(new FluidStack(existing.getFluid(), amount), true);
            }

            entityFuelTankField.set(entity, newTank);

            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] resizeFuelTank: tier=" + tier.name()
                    + " capacity=" + newCapacity);
            }
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: fuel tank replacement failed: " + e);
        }
    }

    // ==========================================================================
    //  FUEL LOADER TIER CHECK
    //  Called from patched TileEntityFuelLoader.isCorrectFuel().
    //  Enforces that the fuel in the loader matches the expected fluid for the
    //  cargo rocket's tier BEFORE the loader converts it to standard GC fuel.
    // ==========================================================================

    /**
     * ASM HOOK — injected at the START of TileEntityFuelLoader.isCorrectFuel(IFuelable).
     *
     * Returns false to BLOCK the fuel transfer (loader refuses to fill the rocket).
     * Returns true to ALLOW the transfer (GC's normal check continues).
     *
     * Only applies to EntityCargoRocket instances — other rocket types are left
     * to GC's standard RocketFuels class-based check.
     *
     * @param fuelable       the rocket entity (from this.attachedFuelable)
     * @param loaderFluidObj the FluidStack in the loader's own tank (may be null)
     */
    public static boolean hookFuelLoaderTierCheck(Object fuelable, Object loaderFluidObj) {
        if (fuelable == null) return true;

        // Only intercept EntityCargoRocket
        if (!"micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket"
                .equals(fuelable.getClass().getName())) {
            return true;
        }

        // T1/T2: no extra restriction — standard GC fuel accepted
        int entityId = ((Entity) fuelable).getEntityId();
        CargoRocketTier tier = RocketStateTracker.getCargoTier(entityId);
        if (tier == CargoRocketTier.T1 || tier == CargoRocketTier.T2) return true;

        // No fluid in loader — let GC handle it (will return false downstream)
        FluidStack loaderFluid = (FluidStack) loaderFluidObj;
        if (loaderFluid == null || loaderFluid.getFluid() == null) return true;

        String loaderFluidName = loaderFluid.getFluid().getName();
        String expectedFluid   = RocketAnimConfig.getFuelFluid(tier);

        // No restriction configured for this tier
        if (expectedFluid == null || expectedFluid.isEmpty()) return true;

        boolean allowed = loaderFluidName.equals(expectedFluid);
        if (!allowed && RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Fuel loader BLOCKED for tier=" + tier.name()
                + " — expected fluid=\"" + expectedFluid + "\""
                + " but loader has \"" + loaderFluidName + "\"");
        }
        return allowed;
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

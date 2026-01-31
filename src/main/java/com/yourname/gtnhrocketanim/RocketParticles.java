package com.yourname.gtnhrocketanim;

import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Handles all particle effects for rocket animations.
 * Uses Galacticraft's particle system for proper rocket flame effects.
 * 
 * GC particle types:
 * - "launchFlameLaunched" / "launchFlameIdle" - orange flame particles
 * - "whiteSmokeIdle" / "whiteSmokeLaunched" - white smoke
 * - "whiteSmokeLargeIdle" / "whiteSmokeLargeLaunched" - large white smoke
 */
public final class RocketParticles {
    
    private static final Random rand = new Random();
    
    // Cache the GC proxy method to avoid repeated reflection
    private static java.lang.reflect.Method gcSpawnParticleMethod = null;
    private static Object gcProxy = null;
    private static boolean gcInitialized = false;
    private static boolean gcAvailable = false;
    
    // GC Vector3 class
    private static Class<?> vector3Class = null;
    private static java.lang.reflect.Constructor<?> vector3Constructor = null;
    
    private RocketParticles() {}
    
    /**
     * Initialize reflection access to GC's particle system.
     */
    private static void initGC() {
        if (gcInitialized) return;
        gcInitialized = true;
        
        try {
            // Get GalacticraftCore class
            Class<?> gcCoreClass = Class.forName("micdoodle8.mods.galacticraft.core.GalacticraftCore");
            
            // Get the proxy field
            java.lang.reflect.Field proxyField = gcCoreClass.getField("proxy");
            gcProxy = proxyField.get(null);
            
            // Get Vector3 class
            vector3Class = Class.forName("micdoodle8.mods.galacticraft.api.vector.Vector3");
            vector3Constructor = vector3Class.getConstructor(double.class, double.class, double.class);
            
            // Get spawnParticle method - it takes (String, Vector3, Vector3, Object[])
            gcSpawnParticleMethod = gcProxy.getClass().getMethod("spawnParticle", 
                String.class, vector3Class, vector3Class, Object[].class);
            
            gcAvailable = true;
            System.out.println("[GTNH Rocket Anim] Successfully hooked into Galacticraft particle system");
        } catch (Exception e) {
            gcAvailable = false;
            System.out.println("[GTNH Rocket Anim] Could not hook GC particles, using fallback: " + e.getMessage());
        }
    }
    
    /**
     * Spawn a GC particle using reflection.
     */
    private static void spawnGCParticle(String type, double x, double y, double z, double mx, double my, double mz) {
        if (!gcAvailable) return;
        
        try {
            Object position = vector3Constructor.newInstance(x, y, z);
            Object motion = vector3Constructor.newInstance(mx, my, mz);
            gcSpawnParticleMethod.invoke(gcProxy, type, position, motion, new Object[] { null });
        } catch (Exception e) {
            // Silently fail - particles are non-critical
        }
    }
    
    /**
     * Spawn retrograde burn particles during descent.
     * Uses GC's launchFlame particles for proper rocket flame look.
     */
    public static void spawnRetrogradeBurn(World w, Entity rocket, double height) {
        if (!RocketAnimConfig.enableRetrogradeBurn) return;
        
        // Only run on CLIENT side - GC particles are client-side only
        if (!w.isRemote) return;
        
        initGC();
        if (!gcAvailable) {
            spawnRetrogradeBurnFallback(w, rocket, height);
            return;
        }
        
        // Only spawn every few ticks
        if (w.getTotalWorldTime() % 2 != 0) return;
        
        // Engine exhaust position
        double exhaustY = rocket.posY - 0.4D;
        
        // Motion scales with height (like GC does for landing)
        double heightScale = Math.max(height, 1.0D) / 60.0D;
        if (heightScale > 2.0D) heightScale = 2.0D;
        
        double intensity = RocketAnimConfig.particleIntensity;
        
        // GC-style spawn pattern - fixed positions with velocity vectors
        // The velocity vector determines the flame direction
        double baseMotionY = -2.0D * heightScale;  // Flames point down
        
        // Spawn at multiple fixed offsets (like GC's cargo rocket)
        double[][] offsets = {
            {0.2D, 0.2D},    // corners
            {-0.2D, 0.2D},
            {-0.2D, -0.2D},
            {0.2D, -0.2D},
            {0.0D, 0.0D},    // center
            {0.2D, 0.0D},    // edges
            {-0.2D, 0.0D},
            {0.0D, 0.2D},
            {0.0D, -0.2D}
        };
        
        for (double[] offset : offsets) {
            if (rand.nextDouble() > intensity) continue;
            
            double ox = offset[0] + (rand.nextDouble() - 0.5D) * 0.1D;
            double oz = offset[1] + (rand.nextDouble() - 0.5D) * 0.1D;
            
            // Motion vector - pointing outward and down
            double mx = ox * 0.5D;
            double my = baseMotionY;
            double mz = oz * 0.5D;
            
            spawnGCParticle("launchFlameIdle", 
                rocket.posX + ox, exhaustY, rocket.posZ + oz,
                mx, my, mz);
        }
    }
    
    /**
     * Fallback using vanilla particles if GC isn't available.
     */
    private static void spawnRetrogradeBurnFallback(World w, Entity rocket, double height) {
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;
        
        if (w.getTotalWorldTime() % 2 != 0) return;
        
        double exhaustY = rocket.posY - 0.4D;
        int count = (int)(6 * RocketAnimConfig.particleIntensity);
        
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5D) * 0.5D;
            double oz = (rand.nextDouble() - 0.5D) * 0.5D;
            
            ws.func_147487_a("flame",
                rocket.posX + ox, exhaustY, rocket.posZ + oz,
                1, 0.1D, 0.1D, 0.1D, 0.05D);
        }
    }
    
    /**
     * Spawn touchdown dust/smoke particles when rocket lands.
     */
    public static void spawnTouchdown(World w, double x, double y, double z) {
        if (!RocketAnimConfig.enableTouchdownParticles) return;
        if (!(w instanceof WorldServer)) return;
        
        WorldServer ws = (WorldServer) w;
        
        int intensity = (int)(20 * RocketAnimConfig.particleIntensity);
        if (intensity < 5) intensity = 5;
        
        // Big dust cloud on landing
        ws.func_147487_a("explode",
            x, y + 0.5D, z,
            intensity,
            1.5D, 0.5D, 1.5D,
            0.2D);
        
        // Smoke cloud
        ws.func_147487_a("smoke",
            x, y + 0.3D, z,
            intensity,
            2.0D, 0.3D, 2.0D,
            0.15D);
        
        // Some flame particles for heat effect
        ws.func_147487_a("flame",
            x, y + 0.3D, z,
            intensity / 3,
            1.0D, 0.2D, 1.0D,
            0.05D);
        
        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Spawned touchdown particles at Y=" + y);
        }
    }
    
    /**
     * Spawn takeoff particles using GC's particle system.
     * Uses launchFlameLaunched for proper rocket exhaust.
     */
    public static void spawnTakeoff(World w, Entity rocket, int launchPhase, long ticksInTakeoff) {
        if (!RocketAnimConfig.enableTakeoffParticles) return;
        
        // Only run on CLIENT side
        if (!w.isRemote) return;
        
        initGC();
        if (!gcAvailable) {
            spawnTakeoffFallback(w, rocket, launchPhase, ticksInTakeoff);
            return;
        }
        
        // Engine exhaust position
        double exhaustY = rocket.posY - 0.4D;
        
        // Motion scales with launch phase
        double thrustScale = launchPhase == 1 ? 0.5D : 1.0D + Math.min(ticksInTakeoff * 0.02D, 1.5D);
        double intensity = RocketAnimConfig.particleIntensity;
        
        // Base downward motion for flames
        double baseMotionY = -2.0D * thrustScale;
        
        // GC-style spawn pattern
        double[][] offsets = {
            {0.2D, 0.2D},    // corners
            {-0.2D, 0.2D},
            {-0.2D, -0.2D},
            {0.2D, -0.2D},
            {0.0D, 0.0D},    // center
            {0.2D, 0.0D},    // edges  
            {-0.2D, 0.0D},
            {0.0D, 0.2D},
            {0.0D, -0.2D}
        };
        
        // Use "launched" type during full thrust for different visual
        String particleType = launchPhase == 1 ? "launchFlameIdle" : "launchFlameLaunched";
        
        // More iterations during full thrust
        int iterations = launchPhase == 1 ? 1 : 2;
        
        for (int iter = 0; iter < iterations; iter++) {
            for (double[] offset : offsets) {
                if (rand.nextDouble() > intensity) continue;
                
                double ox = offset[0] + (rand.nextDouble() - 0.5D) * 0.1D;
                double oz = offset[1] + (rand.nextDouble() - 0.5D) * 0.1D;
                
                // Motion vector - outward and down
                double mx = ox * thrustScale;
                double my = baseMotionY;
                double mz = oz * thrustScale;
                
                spawnGCParticle(particleType,
                    rocket.posX + ox, exhaustY, rocket.posZ + oz,
                    mx, my, mz);
            }
        }
    }
    
    /**
     * Fallback using vanilla particles if GC isn't available.
     */
    private static void spawnTakeoffFallback(World w, Entity rocket, int launchPhase, long ticksInTakeoff) {
        if (!(w instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) w;
        
        double exhaustY = rocket.posY - 0.4D;
        int count = launchPhase == 1 ? 4 : 8;
        count = (int)(count * RocketAnimConfig.particleIntensity);
        
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5D) * 0.5D;
            double oz = (rand.nextDouble() - 0.5D) * 0.5D;
            
            ws.func_147487_a("flame",
                rocket.posX + ox, exhaustY, rocket.posZ + oz,
                1, 0.2D, 0.3D, 0.2D, 0.1D);
        }
    }
}

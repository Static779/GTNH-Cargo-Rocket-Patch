package com.yourname.gtnhrocketanim;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * A FluidTank that only accepts valid rocket fuels, matching GC's own
 * FluidUtil.testFuel() acceptance criterion:
 *   - name starts with "fuel"  (covers "fuel", "fuelgc")
 *   - name contains both "rocket" AND "fuel"  (covers GT++ mixes, EnderIO rocket_fuel, etc.)
 *   - exact match "rc jet fuel"  (RotaryCraft compatibility)
 *
 * This prevents water, lava, oil, etc. from filling the rocket tank while
 * accepting every fluid that the GC fuel loader is able to pump.
 *
 * Tier-specific fuel enforcement (which mix goes in which tier) is handled
 * at the LOADER level by RocketAnimHooks.hookFuelLoaderTierCheck() — the
 * tank itself is fuel-type-agnostic.
 */
public class TieredFluidTank extends FluidTank {

    // ---------------------------------------------------------------
    //  Cached reflection to FluidUtil.testFuel(String)
    //  If GC is present the real method is used; otherwise we fall
    //  back to an inline mirror of the same logic.
    // ---------------------------------------------------------------

    private static java.lang.reflect.Method TEST_FUEL = null;
    private static volatile boolean testFuelReady = false;

    private static synchronized void ensureTestFuel() {
        if (testFuelReady) return;
        testFuelReady = true;
        try {
            Class<?> fu = Class.forName(
                "micdoodle8.mods.galacticraft.core.util.FluidUtil");
            TEST_FUEL = fu.getMethod("testFuel", String.class);
        } catch (Exception ignored) {
            // server without GC, or early class-loading — fallback used
        }
    }

    /**
     * Returns true if the fluid name represents a valid GC rocket fuel.
     * Mirrors FluidUtil.testFuel() exactly.
     */
    static boolean isValidGCFuel(String name) {
        if (name == null) return false;
        ensureTestFuel();
        if (TEST_FUEL != null) {
            try {
                return (Boolean) TEST_FUEL.invoke(null, name);
            } catch (Exception ignored) {}
        }
        // Inline fallback — identical logic to FluidUtil.testFuel()
        if (name.startsWith("fuel")) return true;
        if (name.contains("rocket") && name.contains("fuel")) return true;
        return "rc jet fuel".equals(name);
    }

    // ---------------------------------------------------------------

    public TieredFluidTank(int capacity) {
        super(capacity);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() == null) return 0;
        if (!isValidGCFuel(resource.getFluid().getName())) return 0;
        return super.fill(resource, doFill);
    }
}

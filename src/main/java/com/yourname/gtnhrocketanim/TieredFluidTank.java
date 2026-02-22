package com.yourname.gtnhrocketanim;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * A FluidTank that only accepts a single named Forge fluid.
 * Used to enforce tier-specific fuel requirements on cargo rockets.
 *
 * If acceptedFluid is null, any fluid is accepted (pass-through to super).
 */
public class TieredFluidTank extends FluidTank {

    /** Forge fluid name this tank accepts, or null for any. */
    private final String acceptedFluid;

    public TieredFluidTank(int capacity, String acceptedFluid) {
        super(capacity);
        this.acceptedFluid = (acceptedFluid == null || acceptedFluid.isEmpty())
                ? null : acceptedFluid;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() == null) return 0;
        if (acceptedFluid != null
                && !resource.getFluid().getName().equals(acceptedFluid)) {
            return 0; // wrong fuel type — silently reject
        }
        return super.fill(resource, doFill);
    }

    public String getAcceptedFluid() {
        return acceptedFluid;
    }
}

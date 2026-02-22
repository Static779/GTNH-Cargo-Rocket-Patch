package com.yourname.gtnhrocketanim;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * New cargo rocket items for tiers T3–T8.
 *
 * T1 and T2 are the existing Galacticraft cargo rockets (ItemTier2Rocket damage 10+).
 * We skip them here — this item covers only the NEW tiers added by this mod.
 *
 * Damage values:
 *   0 → T3  (EnumRocketType ordinal 2 = INVENTORY36)
 *   1 → T4  (EnumRocketType ordinal 3 = INVENTORY54)
 *   2 → T5  (EnumRocketType ordinal 3 + GTNHCargoTier=4 via state cache)
 *   3 → T6  (EnumRocketType ordinal 3 + GTNHCargoTier=5 via state cache)
 *   4 → T7  (EnumRocketType ordinal 3 + GTNHCargoTier=6 via state cache)
 *   5 → T8  (EnumRocketType ordinal 3 + GTNHCargoTier=7 via state cache)
 *
 * When right-clicked on a Galacticraft landing pad, spawns EntityCargoRocket
 * using the same logic as ItemTier2Rocket, then sets the GTNHCargoTier state
 * cache so our ASM hooks apply the correct tier behaviour immediately.
 */
public class ItemCargoRocketTiered extends Item {

    /** Per-damage-value inventory icons (client-side only). */
    @SideOnly(Side.CLIENT)
    private IIcon[] tierIcons;

    /** CargoRocketTier for each damage value. */
    public static final CargoRocketTier[] DAMAGE_TO_TIER = {
        CargoRocketTier.T3,   // 0
        CargoRocketTier.T4,   // 1
        CargoRocketTier.T5,   // 2
        CargoRocketTier.T6,   // 3
        CargoRocketTier.T7,   // 4
        CargoRocketTier.T8,   // 5
    };

    /**
     * EnumRocketType ordinal to pass to the EntityCargoRocket constructor.
     *   T3 → 2 (INVENTORY36),  T4-T8 → 3 (INVENTORY54)
     * The rocketType ordinal drives GC's base slot count, but our hookGetSizeInventory
     * overrides it with the tier value from the state cache/PENDING.
     */
    private static final int[] DAMAGE_TO_ENUM_ORDINAL = { 2, 3, 3, 3, 3, 3 };

    // -- Lazily-initialised GC reflection handles --
    private static boolean      gcReady               = false;
    private static Constructor<?> cargoRocketCtor      = null; // (World, D, D, D, EnumRocketType)
    private static Method       getOnPadYOffsetMethod  = null; // EntitySpaceshipBase.getOnPadYOffset()
    private static Block        landingPadFullBlock     = null; // GCBlocks.landingPadFull
    private static Class<?>     landingPadTEClass       = null; // TileEntityLandingPad
    private static Method       getDockedEntityMethod   = null; // TileEntityLandingPad.getDockedEntity()
    private static Field        fuelTankField           = null; // EntitySpaceshipBase.fuelTank

    public ItemCargoRocketTiered() {
        super();
        setMaxStackSize(1);
        setHasSubtypes(true);
        setUnlocalizedName("gtnhrocketanim.cargoRocketTiered");
        setCreativeTab(CreativeTabs.tabTransport);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int dmg = clampDmg(stack.getItemDamage());
        // e.g. "item.t1CargoRocket", "item.t3CargoRocket", …
        return "item." + DAMAGE_TO_TIER[dmg].name().toLowerCase() + "CargoRocket";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < DAMAGE_TO_TIER.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    // -----------------------------------------------------------------------
    //  Client-side icon registration
    // -----------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        tierIcons = new IIcon[DAMAGE_TO_TIER.length];
        for (int i = 0; i < DAMAGE_TO_TIER.length; i++) {
            // Icon name matches the PNG file in assets/gtnhrocketanim/textures/items/
            // e.g. "gtnhrocketanim:t3cargorocket" → textures/items/t3cargorocket.png
            String iconName = DAMAGE_TO_TIER[i].name().toLowerCase() + "cargorocket";
            tierIcons[i] = register.registerIcon("gtnhrocketanim:" + iconName);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        if (tierIcons == null) return null;
        return tierIcons[clampDmg(damage)];
    }

    // -----------------------------------------------------------------------
    //  Core interaction — mirrors ItemTier2Rocket.onItemUse
    // -----------------------------------------------------------------------

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        // Only do real work on the server; client gets entity via normal sync.
        if (world.isRemote) return false;

        int dmg = clampDmg(stack.getItemDamage());
        CargoRocketTier tier    = DAMAGE_TO_TIER[dmg];
        int             enumOrd = DAMAGE_TO_ENUM_ORDINAL[dmg];

        ensureGCReflection();
        if (cargoRocketCtor == null || landingPadFullBlock == null) {
            if (RocketAnimConfig.debugLogging) {
                System.out.println("[GTNH Rocket Anim] ItemCargoRocketTiered: GC reflection not ready.");
            }
            return false;
        }

        // ---- Find the nearest empty landing pad centre (±1 block in X and Z) ----
        int padX = Integer.MIN_VALUE, padY = 0, padZ = 0;
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int bx = x + dx, bz = z + dz;
                if (world.getBlock(bx, y, bz) == landingPadFullBlock
                        && world.getBlockMetadata(bx, y, bz) == 0) {
                    Object te = world.getTileEntity(bx, y, bz);
                    if (te != null && landingPadTEClass != null
                            && landingPadTEClass.isInstance(te)) {
                        try {
                            if (getDockedEntityMethod.invoke(te) == null) {
                                padX = bx; padY = y; padZ = bz;
                                break outer;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        if (padX == Integer.MIN_VALUE) return false; // no empty pad nearby

        // ---- Resolve EnumRocketType by ordinal ----
        Object rocketType;
        try {
            Class<?> enumClass = Class.forName(
                "micdoodle8.mods.galacticraft.api.entity.IRocketType$EnumRocketType");
            Object[] vals = (Object[]) enumClass.getMethod("values").invoke(null);
            rocketType = vals[enumOrd];
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] Cannot resolve EnumRocketType: " + e);
            return false;
        }

        // ---- Signal our hooks which tier is being built (covers getSizeInventory
        //      during field-init AND hookPostConstructorTierInit) ----
        RocketAnimHooks.setPendingSpawnTier(tier);

        Entity entity;
        try {
            entity = (Entity) cargoRocketCtor.newInstance(
                world, padX + 0.5, padY + 0.4, padZ + 0.5, rocketType);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] Failed to construct EntityCargoRocket: " + e);
            RocketAnimHooks.clearPendingSpawnTier();
            return false;
        }
        RocketAnimHooks.clearPendingSpawnTier();

        // hookPostConstructorTierInit already read PENDING and set the cache, but
        // set it again here to be safe (especially for T5-T8).
        RocketStateTracker.setCargoTier(entity.getEntityId(), tier);

        // ---- Position on pad ----
        double yOffset = 0.4;
        if (getOnPadYOffsetMethod != null) {
            try {
                yOffset = ((Number) getOnPadYOffsetMethod.invoke(entity)).doubleValue();
            } catch (Exception ignored) {}
        }
        entity.setPosition(padX + 0.5, padY + yOffset, padZ + 0.5);

        // ---- Transfer any saved fuel from the item NBT ----
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("RocketFuel")
                && fuelTankField != null) {
            try {
                int fuelAmount = stack.getTagCompound().getInteger("RocketFuel");
                if (fuelAmount > 0) {
                    FluidTank tank = (FluidTank) fuelTankField.get(entity);
                    if (tank != null) {
                        Fluid gcFuel = FluidRegistry.getFluid("fuel");
                        if (gcFuel != null) {
                            tank.fill(new FluidStack(gcFuel, fuelAmount), true);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        world.spawnEntityInWorld(entity);

        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }

        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Spawned " + tier.name()
                + " cargo rocket at (" + padX + "," + padY + "," + padZ + ")");
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Lazy reflection initialiser
    // -----------------------------------------------------------------------

    private static synchronized void ensureGCReflection() {
        if (gcReady) return;
        gcReady = true;

        // EntityCargoRocket 5-arg constructor
        try {
            Class<?> cargoClass = Class.forName(
                "micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket");
            Class<?> enumType = Class.forName(
                "micdoodle8.mods.galacticraft.api.entity.IRocketType$EnumRocketType");
            cargoRocketCtor = cargoClass.getConstructor(
                World.class, double.class, double.class, double.class, enumType);
            cargoRocketCtor.setAccessible(true);

            // getOnPadYOffset — walk up the superclass chain
            for (Class<?> c = cargoClass; c != null; c = c.getSuperclass()) {
                try {
                    getOnPadYOffsetMethod = c.getDeclaredMethod("getOnPadYOffset");
                    getOnPadYOffsetMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: EntityCargoRocket ctor not found: " + e);
        }

        // GCBlocks.landingPadFull
        try {
            Class<?> gcBlocks = Class.forName(
                "micdoodle8.mods.galacticraft.core.blocks.GCBlocks");
            landingPadFullBlock = (Block) gcBlocks.getField("landingPadFull").get(null);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: GCBlocks.landingPadFull not found: " + e);
        }

        // TileEntityLandingPad.getDockedEntity()
        try {
            landingPadTEClass = Class.forName(
                "micdoodle8.mods.galacticraft.core.tile.TileEntityLandingPad");
            getDockedEntityMethod = landingPadTEClass.getMethod("getDockedEntity");
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: TileEntityLandingPad reflection failed: " + e);
        }

        // EntitySpaceshipBase.fuelTank (for fuel transfer)
        try {
            Class<?> base = Class.forName(
                "micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase");
            fuelTankField = base.getDeclaredField("fuelTank");
            fuelTankField.setAccessible(true);
        } catch (Exception e) {
            System.out.println("[GTNH Rocket Anim] WARN: fuelTank reflection failed: " + e);
        }
    }

    private static int clampDmg(int dmg) {
        return Math.max(0, Math.min(dmg, DAMAGE_TO_TIER.length - 1));
    }
}

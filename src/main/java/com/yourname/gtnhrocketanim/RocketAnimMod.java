package com.yourname.gtnhrocketanim;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

@Mod(
    modid = "gtnhrocketanim",
    name = "GTNH Rocket Landing/Takeoff Animation",
    version = "1.0.0",
    acceptableRemoteVersions = "*"
)
public class RocketAnimMod {

    /** The new tiered cargo rocket item (T3–T8). */
    public static Item CARGO_ROCKET_ITEM;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RocketAnimConfig.load(event);

        CARGO_ROCKET_ITEM = new ItemCargoRocketTiered();
        GameRegistry.registerItem(CARGO_ROCKET_ITEM, "cargoRocketTiered");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        registerRecipes();
    }

    // -----------------------------------------------------------------------
    //  Recipe registration
    //
    //  Each new cargo rocket tier is crafted from the equivalent personal
    //  rocket of the same tier plus a number of Chests that scales with cargo
    //  capacity.  T1 and T2 are skipped — they already exist in Galacticraft.
    //
    //  Registry names are the item's getUnlocalizedName() value as passed to
    //  GameRegistry.registerItem by each mod.
    //
    //  If a required personal rocket item isn't found (e.g. GalaxySpace isn't
    //  installed) that recipe is silently skipped; a warning is logged.
    // -----------------------------------------------------------------------

    private static void registerRecipes() {

        // -- T3 Cargo Rocket --
        // Based on the GC Tier-3 (Asteroids) personal rocket.
        // Registered by AsteroidsItems.registerItem() with modid "GalacticraftMars"
        // and name = item.getUnlocalizedName() = "item.itemTier3Rocket"
        addCargoRecipe(0, "GalacticraftMars", "item.itemTier3Rocket", 0, 2);

        // -- T4 Cargo Rocket --
        // Based on the GalaxySpace Tier-4 personal rocket.
        // Registered by GSItems.registerItem() with modid "GalaxySpace"
        // and name = item.getUnlocalizedName() = "item.Tier4Rocket"
        addCargoRecipe(1, "GalaxySpace", "item.Tier4Rocket", 0, 3);

        // -- T5 Cargo Rocket --
        addCargoRecipe(2, "GalaxySpace", "item.Tier5Rocket", 0, 3);

        // -- T6 Cargo Rocket --
        addCargoRecipe(3, "GalaxySpace", "item.Tier6Rocket", 0, 4);

        // -- T7 Cargo Rocket --
        addCargoRecipe(4, "GalaxySpace", "item.Tier7Rocket", 0, 4);

        // -- T8 Cargo Rocket --
        addCargoRecipe(5, "GalaxySpace", "item.Tier8Rocket", 0, 5);
    }

    /**
     * Registers a shapeless recipe:
     *   personalRocket (modid:itemName, damage=rocketDmg) + [chestCount] Chests
     *   → ItemCargoRocketTiered at damage=cargoDamage
     *
     * If the personal rocket item can't be found the recipe is skipped with a warning.
     */
    private static void addCargoRecipe(int cargoDamage, String modid, String itemName,
                                       int rocketDmg, int chestCount) {
        Item rocketItem = GameRegistry.findItem(modid, itemName);
        if (rocketItem == null) {
            System.out.println("[GTNH Rocket Anim] Recipe skipped: " + modid + ":" + itemName
                + " not found. Install the required mod to unlock "
                + ItemCargoRocketTiered.DAMAGE_TO_TIER[cargoDamage].name() + " Cargo Rocket.");
            return;
        }

        // Build ingredient array: rocket + N chests
        Object[] ingredients = new Object[1 + chestCount];
        ingredients[0] = new ItemStack(rocketItem, 1, rocketDmg);
        for (int i = 1; i <= chestCount; i++) {
            ingredients[i] = new ItemStack(Blocks.chest);
        }

        ItemStack result = new ItemStack(CARGO_ROCKET_ITEM, 1, cargoDamage);
        GameRegistry.addShapelessRecipe(result, ingredients);

        if (RocketAnimConfig.debugLogging) {
            System.out.println("[GTNH Rocket Anim] Registered recipe: "
                + ItemCargoRocketTiered.DAMAGE_TO_TIER[cargoDamage].name()
                + " Cargo Rocket ← " + modid + ":" + itemName + " + " + chestCount + "x Chest");
        }
    }
}

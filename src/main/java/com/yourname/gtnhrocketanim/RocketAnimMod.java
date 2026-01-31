package com.yourname.gtnhrocketanim;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = "gtnhrocketanim",
    name = "GTNH Rocket Landing/Takeoff Animation",
    version = "1.0.0",
    acceptableRemoteVersions = "*"
)
public class RocketAnimMod {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RocketAnimConfig.load(event);
    }
}

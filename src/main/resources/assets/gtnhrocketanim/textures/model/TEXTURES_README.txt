CARGO ROCKET TIER TEXTURES
==========================

Place your PNG texture files here. Each file must match the UV unwrap of
  assets/galacticraftmars/models/cargoRocket.obj
so no new OBJ model files are needed — only the surface skin changes.

FILE LIST & STYLE GUIDE
------------------------

cargoRocketT1.png
  Base: galacticraftcore:textures/model/rocketT1.png (grey/white T1 body)
  Style: Slim proportions, single row of cargo straps, lighter plating.
  Fuel:  Rocket Fuel (standard GC fuel)

  (T2 reuses the vanilla galacticraftmars:textures/model/cargoRocket.png — no file needed here)

cargoRocketT3.png
  Base: galacticraftasteroids:textures/model/tier3rocket.png (dark metal T3 body)
  Style: Dark gunmetal plating, reinforced cargo straps, heavier build.
  Fuel:  Rocket Fuel (standard GC fuel)

cargoRocketT4.png
  Base: GalaxySpace T4 rocket texture style
  Style: Sleek silver-blue body, orange trim lines, GalaxySpace aesthetic.
  Fuel:  Dense Hydrazine

cargoRocketT5.png
  Base: GalaxySpace T5 rocket texture style
  Style: Deeper blue, glowing purple accent lines matching CN3H7O3 fuel colour.
  Fuel:  Purple Fuel (CN3H7O3)

cargoRocketT6.png
  Base: GalaxySpace T6 rocket texture style
  Style: Heavy industrial plating with enhanced purple glow details.
  Fuel:  Purple Fuel (CN3H7O3)

cargoRocketT7.png
  Base: GalaxySpace T7 rocket texture style
  Style: Near-black hull with green energy conduit lines matching H8N4C2O4 fuel.
  Fuel:  Green Fuel (H8N4C2O4)

cargoRocketT8.png
  Base: GalaxySpace T8 rocket texture style
  Style: Black/chrome plating, intense green glow, heaviest armour detailing.
  Fuel:  Green Fuel (H8N4C2O4)

TIPS
----
- Match resolution to the vanilla cargoRocket.png (typically 64x128 or 128x256 px).
- Until you have proper textures, each tier will show Minecraft's missing-texture
  purple/black checkerboard — the mod will still function correctly.
- Texture paths can be overridden per-tier in config/gtnhrocketanim.cfg
  (tierX_cargo → texturePath) without recompiling.

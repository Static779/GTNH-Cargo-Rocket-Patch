# Installation Guide

Complete instructions for building and installing the GTNH Rocket Animation mod.

---

## Prerequisites

### Required Software

1. **Java 8 JDK** (not JRE)
   - Download: [Adoptium/Temurin JDK 8](https://adoptium.net/temurin/releases/?version=8)
   - Or: [Oracle JDK 8](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
   - Verify installation:
     ```powershell
     java -version
     # Should show: openjdk version "1.8.x" or java version "1.8.x"
     ```

2. **Gradle** (optional - wrapper included)
   - The project includes a Gradle wrapper, so standalone Gradle is not required
   - If you prefer system Gradle: [gradle.org/install](https://gradle.org/install/)

3. **GTNH Instance**
   - A working GregTech: New Horizons installation
   - Typically installed via MultiMC, Prism Launcher, or CurseForge

---

## Building the Mod

### Step 1: Open Terminal in Project Directory

```powershell
cd C:\Users\amirw\OneDrive\Desktop\gen2\gtnhrocketanim
```

### Step 2: Set JAVA_HOME (if not already set)

```powershell
# Find your Java 8 installation path, then:
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.xxx-hotspot"

# Verify:
$env:JAVA_HOME
```

### Step 3: Run Gradle Build

**Using Gradle Wrapper (recommended):**
```powershell
.\gradlew build
```

**Or with system Gradle:**
```powershell
gradle build
```

### Step 4: Locate the Built JAR

After successful build, find the JAR at:
```
gtnhrocketanim\build\libs\gtnhrocketanim-1.0.0.jar
```

---

## Installing the Mod

### Step 1: Locate Your GTNH Mods Folder

**MultiMC / Prism Launcher:**
```
<MultiMC folder>\instances\<GTNH instance>\minecraft\mods\
```

**CurseForge:**
```
C:\Users\<username>\curseforge\minecraft\Instances\<GTNH instance>\mods\
```

**Standard Launcher:**
```
%appdata%\.minecraft\mods\
```

### Step 2: Copy the JAR

Copy `gtnhrocketanim-1.0.0.jar` to the mods folder:

```powershell
# Example for MultiMC:
Copy-Item ".\build\libs\gtnhrocketanim-1.0.0.jar" "C:\MultiMC\instances\GTNH\minecraft\mods\"
```

### Step 3: First Launch

1. Launch GTNH through your launcher
2. The mod will automatically generate its config file
3. Check the Minecraft log for:
   ```
   [GTNH Rocket Anim] Transforming micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket
   [GTNH Rocket Anim] Patching moveToDestination(I)V
   [GTNH Rocket Anim] Patching func_70071_h_()V (tick)
   ```

### Step 4: Verify Installation

In the Minecraft main menu:
1. Click "Mods"
2. Find "GTNH Rocket Landing/Takeoff Animation" in the list
3. Confirm version shows "1.0.0"

---

## Configuration

### Config File Location

After first launch, the config file is created at:
```
<minecraft folder>\config\gtnhrocketanim.cfg
```

### Editing Configuration

Open `gtnhrocketanim.cfg` in any text editor:

```ini
# Landing Settings
landing {
    # How high above the pad the rocket spawns (blocks)
    I:landingSpawnHeight=120
    
    # Max descent speed (blocks/tick, 1.0 = 20 blocks/second)
    D:maxDescentSpeed=1.0
    
    # Min descent speed near pad
    D:minDescentSpeed=0.04
    
    # Horizontal correction strength
    D:horizontalCorrection=0.06
    
    # Distance to snap to final position
    D:snapDistance=0.3
}

# Takeoff Settings  
takeoff {
    # Ticks to reach full thrust (0 = instant, 80 = 4 seconds)
    I:takeoffRampTicks=80
    
    # Initial thrust fraction (0.0 to 1.0)
    D:takeoffMinMultiplier=0.1
}

# Debug Settings
debug {
    # Enable console logging
    B:debugLogging=false
}
```

### Applying Config Changes

- Changes require a game restart to take effect
- No need to rebuild the mod

---

## Troubleshooting

### Build Errors

**"Could not find tools.jar"**
- Ensure JAVA_HOME points to a JDK, not a JRE
- JDK includes `lib\tools.jar`, JRE does not

**"Gradle version X is required"**
- Use the included Gradle wrapper (`.\gradlew`) instead of system Gradle

**"Cannot resolve dependencies"**
- Check internet connection
- Try: `.\gradlew build --refresh-dependencies`

### Runtime Errors

**Mod not appearing in mod list:**
- Verify JAR is in the correct mods folder
- Check that Forge is loading (not vanilla Minecraft)
- Look for errors in `logs\fml-client-latest.log`

**"NoClassDefFoundError" for Galacticraft classes:**
- Ensure GTNH Galacticraft is installed (`Galacticraft-3.3.13-GTNH.jar` or similar)
- This mod requires Galacticraft to function

**Rocket still teleporting (no animation):**
- Check log for "Successfully patched moveToDestination" message
- If missing, the class name may have changed in your GC version
- Enable `debugLogging=true` in config for more details

**Crash on rocket landing:**
- Check crash log for stack trace
- Common cause: field names differ in your GC fork
- Report issue with your GC version number

### Log Files

Useful logs for debugging:

| Log File | Location | Contains |
|----------|----------|----------|
| Latest client log | `logs\latest.log` | All mod messages |
| FML log | `logs\fml-client-latest.log` | Mod loading details |
| Crash report | `crash-reports\` | Detailed crash info |

---

## Updating the Mod

### From Source Changes

1. Make your code changes
2. Rebuild: `.\gradlew clean build`
3. Replace the old JAR in mods folder
4. Restart Minecraft

### From New Release

1. Download new JAR
2. Delete old `gtnhrocketanim-*.jar` from mods folder
3. Copy new JAR to mods folder
4. Restart Minecraft

---

## Uninstalling

1. Remove `gtnhrocketanim-1.0.0.jar` from mods folder
2. (Optional) Remove `config\gtnhrocketanim.cfg`
3. Restart Minecraft

---

## Development Setup (Optional)

If you want to modify the mod code:

### IDE Setup (IntelliJ IDEA)

1. Open IntelliJ IDEA
2. File → Open → Select `gtnhrocketanim` folder
3. Import as Gradle project
4. Wait for indexing to complete
5. Run `.\gradlew setupDecompWorkspace` (first time only)
6. Refresh Gradle project in IDE

### Running in Development

```powershell
.\gradlew runClient
```

This launches Minecraft with your mod in a development environment.

---

## Getting Help

If you encounter issues:

1. Check the Troubleshooting section above
2. Search existing issues (if using a repository)
3. Provide:
   - Your GTNH version
   - Your Galacticraft JAR filename
   - Relevant log excerpts
   - Steps to reproduce the problem

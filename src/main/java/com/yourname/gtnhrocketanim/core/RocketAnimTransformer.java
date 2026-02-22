package com.yourname.gtnhrocketanim.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * ASM transformer for GTNH Galacticraft fork.
 *
 * Patches EntityCargoRocket:
 *  1) moveToDestination(int)    — intercepts teleport for landing height
 *  2) func_70071_h_() / onUpdate() — injects per-tick animation hook
 *  3) onReachAtmosphere()       — delays teleport until takeoff altitude reached
 *  4) getFuelTankCapacity()     — returns tier-appropriate fuel capacity
 *  5) <init>(World,D,D,D,EnumRocketType) — resizes FluidTank after rocketType set
 *  6) readEntityFromNBT(NBTTagCompound)  — caches tier, resizes tank after load
 *  7) writeEntityToNBT(NBTTagCompound)   — persists GTNHCargoTier NBT tag
 *  8) getSizeInventory()        — returns tier-appropriate slot count
 *
 * Patches RenderCargoRocket:
 *  9) renderBuggy(...)          — swaps static texture with tier-specific one
 * 10) func_110779_a(...)        — same, for the entity-texture delegate
 *
 * Patches TileEntityFuelLoader:
 * 11) isCorrectFuel(IFuelable)  — enforces tier-specific fuel type before GC's class check
 */
public class RocketAnimTransformer implements IClassTransformer {

    // ---- EntityCargoRocket ----
    private static final String TARGET_CLASS_DOT =
            "micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket";
    private static final String TARGET_CLASS =
            "micdoodle8/mods/galacticraft/planets/mars/entities/EntityCargoRocket";

    // ---- RenderCargoRocket ----
    private static final String RENDER_CLASS_DOT =
            "micdoodle8.mods.galacticraft.planets.mars.client.render.entity.RenderCargoRocket";
    private static final String RENDER_CLASS =
            "micdoodle8/mods/galacticraft/planets/mars/client/render/entity/RenderCargoRocket";

    // ---- TileEntityFuelLoader ----
    private static final String FUEL_LOADER_DOT =
            "micdoodle8.mods.galacticraft.core.tile.TileEntityFuelLoader";
    private static final String FUEL_LOADER =
            "micdoodle8/mods/galacticraft/core/tile/TileEntityFuelLoader";
    private static final String I_FUELABLE =
            "micdoodle8/mods/galacticraft/api/entity/IFuelable";

    // Superclass where landing/targetVec/launchPhase/timeSinceLaunch live
    private static final String AUTO_ROCKET =
            "micdoodle8/mods/galacticraft/api/prefab/entity/EntityAutoRocket";

    private static final String BLOCKVEC3 =
            "micdoodle8/mods/galacticraft/api/vector/BlockVec3";

    // EnumRocketType (used in 5-arg constructor descriptor)
    private static final String ENUM_ROCKET_TYPE =
            "micdoodle8/mods/galacticraft/api/entity/IRocketType$EnumRocketType";

    // ---- State flags ----
    private boolean patchedMoveToDestination   = false;
    private boolean patchedTick                = false;
    private boolean patchedOnReachAtmosphere   = false;
    private boolean patchedFuelTankCapacity    = false;
    private boolean patchedConstructor         = false;
    private boolean patchedReadNbt             = false;
    private boolean patchedWriteNbt            = false;
    private boolean patchedSizeInventory       = false;
    private boolean patchedRenderBuggy         = false;
    private boolean patchedGetEntityTexture    = false;
    private boolean patchedFuelLoader          = false;

    // ---- Hooks class (internal ASM name) ----
    private static final String HOOKS =
            "com/yourname/gtnhrocketanim/RocketAnimHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (TARGET_CLASS_DOT.equals(name)) {
            return transformEntityCargoRocket(basicClass);
        }
        if (RENDER_CLASS_DOT.equals(name)) {
            return transformRenderCargoRocket(basicClass);
        }
        if (FUEL_LOADER_DOT.equals(name)) {
            return transformFuelLoader(basicClass);
        }
        return basicClass;
    }

    // ==========================================================================
    //  EntityCargoRocket transform
    // ==========================================================================

    private byte[] transformEntityCargoRocket(byte[] basicClass) {
        System.out.println("[GTNH Rocket Anim] Transforming EntityCargoRocket");

        ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        System.out.println("[GTNH Rocket Anim] Methods found:");
        for (MethodNode mn : cn.methods) {
            System.out.println("[GTNH Rocket Anim]   " + mn.name + mn.desc);
        }

        for (MethodNode mn : cn.methods) {

            // (1) moveToDestination
            if ("moveToDestination".equals(mn.name) && "(I)V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching moveToDestination(I)V");
                patchMoveToDestination(mn);
                patchedMoveToDestination = true;
            }

            // (2) onUpdate / tick
            if ((mn.name.equals("func_70071_h_") || mn.name.equals("onUpdate"))
                    && "()V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching " + mn.name + "()V (tick)");
                injectTickHook(mn);
                patchedTick = true;
            }

            // (3) onReachAtmosphere
            if ("onReachAtmosphere".equals(mn.name) && "()V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching onReachAtmosphere()V");
                injectAtmosphereGuard(mn);
                patchedOnReachAtmosphere = true;
            }

            // (4) getFuelTankCapacity — full body replacement
            if ("getFuelTankCapacity".equals(mn.name) && "()I".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching getFuelTankCapacity()I");
                patchGetFuelTankCapacity(mn);
                patchedFuelTankCapacity = true;
            }

            // (5) 5-arg constructor — inject after PUTFIELD rocketType
            if ("<init>".equals(mn.name) && mn.desc.contains("EnumRocketType")) {
                System.out.println("[GTNH Rocket Anim] Patching constructor " + mn.desc);
                injectConstructorTierInit(mn);
                patchedConstructor = true;
            }

            // (6) readEntityFromNBT — inject before final RETURN
            if (("readEntityFromNBT".equals(mn.name) || "func_70037_a".equals(mn.name))
                    && "(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching readEntityFromNBT");
                injectNbtReadHook(mn);
                patchedReadNbt = true;
            }

            // (7) writeEntityToNBT — inject before final RETURN
            if (("writeEntityToNBT".equals(mn.name) || "func_70014_b".equals(mn.name))
                    && "(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching writeEntityToNBT");
                injectNbtWriteHook(mn);
                patchedWriteNbt = true;
            }

            // (8) getSizeInventory — full body replacement
            // In production the method retains its obfuscated name func_70302_i_
            if (("getSizeInventory".equals(mn.name) || "func_70302_i_".equals(mn.name))
                    && "()I".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching getSizeInventory / func_70302_i_()I");
                patchGetSizeInventory(mn);
                patchedSizeInventory = true;
            }
        }

        logMissing("moveToDestination", patchedMoveToDestination);
        logMissing("tick (func_70071_h_)", patchedTick);
        logMissing("onReachAtmosphere", patchedOnReachAtmosphere);
        logMissing("getFuelTankCapacity", patchedFuelTankCapacity);
        logMissing("constructor (EnumRocketType)", patchedConstructor);
        logMissing("readEntityFromNBT", patchedReadNbt);
        logMissing("writeEntityToNBT", patchedWriteNbt);
        logMissing("getSizeInventory", patchedSizeInventory);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // ==========================================================================
    //  RenderCargoRocket transform
    // ==========================================================================

    private byte[] transformRenderCargoRocket(byte[] basicClass) {
        System.out.println("[GTNH Rocket Anim] Transforming RenderCargoRocket");

        ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            // renderBuggy — the main render method
            if ("renderBuggy".equals(mn.name)) {
                System.out.println("[GTNH Rocket Anim] Patching RenderCargoRocket.renderBuggy" + mn.desc);
                // 1) Model-swap guard: T3-T8 delegate to GC/GS renderer and return early
                patchRenderBuggyModelSwap(mn);
                // 2) Texture swap for T1/T2 (the fall-through path)
                patchRenderTexture(mn, 1 /* entity is ALOAD_1 */);
                patchedRenderBuggy = true;
            }

            // func_110779_a / getEntityTexture — texture delegate
            if ("func_110779_a".equals(mn.name) || "getEntityTexture".equals(mn.name)) {
                if (mn.desc.contains("ResourceLocation")) {
                    System.out.println("[GTNH Rocket Anim] Patching RenderCargoRocket." + mn.name + mn.desc);
                    patchGetEntityTexture(mn);
                    patchedGetEntityTexture = true;
                }
            }
        }

        logMissing("RenderCargoRocket.renderBuggy", patchedRenderBuggy);
        logMissing("RenderCargoRocket.func_110779_a / getEntityTexture", patchedGetEntityTexture);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // ==========================================================================
    //  TileEntityFuelLoader transform
    // ==========================================================================

    /**
     * Patches TileEntityFuelLoader.isCorrectFuel(IFuelable) to enforce
     * tier-specific fuel restrictions before GC's normal class-based check.
     *
     * The injection calls RocketAnimHooks.hookFuelLoaderTierCheck(fuelable, loaderFluid)
     * at the very start of the method.  If the hook returns false, we immediately
     * return false (block the loader).  If true, the original GC logic runs normally.
     *
     * This MUST happen before GC converts the loader fluid to standard "fuel",
     * so we intercept the raw loader tank fluid here.
     */
    private byte[] transformFuelLoader(byte[] basicClass) {
        System.out.println("[GTNH Rocket Anim] Transforming TileEntityFuelLoader");

        ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        System.out.println("[GTNH Rocket Anim] TileEntityFuelLoader methods found:");
        for (MethodNode mn : cn.methods) {
            System.out.println("[GTNH Rocket Anim]   " + mn.name + mn.desc);
        }

        for (MethodNode mn : cn.methods) {
            // Match isCorrectFuel(IFuelable)Z — the descriptor contains the IFuelable interface
            if ("isCorrectFuel".equals(mn.name) && mn.desc.contains(I_FUELABLE)) {
                System.out.println("[GTNH Rocket Anim] Patching TileEntityFuelLoader.isCorrectFuel" + mn.desc);
                injectFuelLoaderTierCheck(mn);
                patchedFuelLoader = true;
                break;
            }
        }

        logMissing("TileEntityFuelLoader.isCorrectFuel", patchedFuelLoader);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // ==========================================================================
    //  EntityCargoRocket patch implementations
    // ==========================================================================

    /** (1) Replace moveToDestination body completely. */
    private void patchMoveToDestination(MethodNode mn) {
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables.clear();

        InsnList insn = new InsnList();

        // int result = RocketAnimHooks.interceptMoveToDestination(this, this.targetVec,
        //                                                          this.destinationFrequency, arg1);
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));

        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "targetVec", "L" + BLOCKVEC3 + ";"));

        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "destinationFrequency", "I"));

        insn.add(new VarInsnNode(Opcodes.ILOAD, 1));

        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "interceptMoveToDestination",
                "(Lnet/minecraft/entity/Entity;Ljava/lang/Object;II)I", false));

        insn.add(new VarInsnNode(Opcodes.ISTORE, 2)); // resolved height

        // if (result == -1) return;
        insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
        insn.add(new InsnNode(Opcodes.ICONST_M1));
        LabelNode continueLabel = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IF_ICMPNE, continueLabel));
        insn.add(new InsnNode(Opcodes.RETURN));
        insn.add(continueLabel);

        // this.landing = true;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new InsnNode(Opcodes.ICONST_1));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, AUTO_ROCKET, "landing", "Z"));

        // Zero motion
        for (String motionField : new String[]{ "field_70159_w", "field_70181_x", "field_70179_y" }) {
            insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
            insn.add(new InsnNode(Opcodes.DCONST_0));
            insn.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/entity/Entity", motionField, "D"));
        }

        // this.setPosition(targetVec.x + 0.5, targetVec.y + resolved + freq_offset, targetVec.z + 0.5)
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));

        // X
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "x", "I"));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new LdcInsnNode(0.5D));
        insn.add(new InsnNode(Opcodes.DADD));

        // Y = targetVec.y + resolved + (destinationFrequency == 1 ? 0 : 1)
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "y", "I"));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new InsnNode(Opcodes.DADD));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "destinationFrequency", "I"));
        insn.add(new InsnNode(Opcodes.ICONST_1));
        LabelNode eqLabel  = new LabelNode();
        LabelNode endLabel = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, eqLabel));
        insn.add(new InsnNode(Opcodes.DCONST_1));
        insn.add(new JumpInsnNode(Opcodes.GOTO, endLabel));
        insn.add(eqLabel);
        insn.add(new InsnNode(Opcodes.DCONST_0));
        insn.add(endLabel);
        insn.add(new InsnNode(Opcodes.DADD));

        // Z
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "z", "I"));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new LdcInsnNode(0.5D));
        insn.add(new InsnNode(Opcodes.DADD));

        insn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET_CLASS,
                "func_70107_b", "(DDD)V", false));

        insn.add(new InsnNode(Opcodes.RETURN));

        mn.instructions.add(insn);
        mn.maxStack  = 0;
        mn.maxLocals = 0;
        System.out.println("[GTNH Rocket Anim] moveToDestination patched");
    }

    /** (2) Inject tick hook before the final RETURN. */
    private void injectTickHook(MethodNode mn) {
        AbstractInsnNode ret = findLastReturn(mn);
        if (ret == null) {
            System.out.println("[GTNH Rocket Anim] WARN: no RETURN in tick method");
            return;
        }

        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "landing", "Z"));
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "launchPhase", "I"));
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "timeSinceLaunch", "F"));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "onCargoRocketTick",
                "(Lnet/minecraft/entity/Entity;ZLjava/lang/Object;IF)V", false));

        mn.instructions.insertBefore(ret, call);
        System.out.println("[GTNH Rocket Anim] tick hook injected");
    }

    /** (3) Inject atmosphere guard at method start. */
    private void injectAtmosphereGuard(MethodNode mn) {
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "shouldDelayAtmosphereTransition",
                "(Lnet/minecraft/entity/Entity;)Z", false));
        LabelNode continueLabel = new LabelNode();
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);
        mn.instructions.insert(guard);
        System.out.println("[GTNH Rocket Anim] atmosphere guard injected");
    }

    /** (4) Replace getFuelTankCapacity() body: call hookGetFuelTankCapacity(this). */
    private void patchGetFuelTankCapacity(MethodNode mn) {
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables.clear();

        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookGetFuelTankCapacity",
                "(Ljava/lang/Object;)I", false));
        insn.add(new InsnNode(Opcodes.IRETURN));

        mn.instructions.add(insn);
        mn.maxStack  = 0;
        mn.maxLocals = 0;
        System.out.println("[GTNH Rocket Anim] getFuelTankCapacity patched");
    }

    /**
     * (5) In the 5-arg constructor, inject hookPostConstructorTierInit(this)
     * immediately after the first PUTFIELD whose field name is "rocketType".
     */
    private void injectConstructorTierInit(MethodNode mn) {
        boolean injected = false;
        for (AbstractInsnNode node : mn.instructions.toArray()) {
            if (node.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fin = (FieldInsnNode) node;
                if ("rocketType".equals(fin.name)) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                            "hookPostConstructorTierInit",
                            "(Ljava/lang/Object;)V", false));
                    mn.instructions.insert(node, call);
                    injected = true;
                    break; // only the first occurrence
                }
            }
        }
        if (injected) {
            System.out.println("[GTNH Rocket Anim] constructor tier-init injected after PUTFIELD rocketType");
        } else {
            System.out.println("[GTNH Rocket Anim] WARN: PUTFIELD rocketType not found in constructor");
        }
    }

    /**
     * (6) Inject hookReadNbt(this, nbt) before the final RETURN of readEntityFromNBT.
     * The NBTTagCompound parameter is at slot 1.
     */
    private void injectNbtReadHook(MethodNode mn) {
        AbstractInsnNode ret = findLastReturn(mn);
        if (ret == null) {
            System.out.println("[GTNH Rocket Anim] WARN: no RETURN in readEntityFromNBT");
            return;
        }

        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0)); // entity
        call.add(new VarInsnNode(Opcodes.ALOAD, 1)); // NBTTagCompound
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookReadNbt",
                "(Lnet/minecraft/entity/Entity;Lnet/minecraft/nbt/NBTTagCompound;)V", false));

        mn.instructions.insertBefore(ret, call);
        System.out.println("[GTNH Rocket Anim] readEntityFromNBT hook injected");
    }

    /**
     * (7) Inject hookWriteNbt(this, nbt) before the final RETURN of writeEntityToNBT.
     */
    private void injectNbtWriteHook(MethodNode mn) {
        AbstractInsnNode ret = findLastReturn(mn);
        if (ret == null) {
            System.out.println("[GTNH Rocket Anim] WARN: no RETURN in writeEntityToNBT");
            return;
        }

        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookWriteNbt",
                "(Lnet/minecraft/entity/Entity;Lnet/minecraft/nbt/NBTTagCompound;)V", false));

        mn.instructions.insertBefore(ret, call);
        System.out.println("[GTNH Rocket Anim] writeEntityToNBT hook injected");
    }

    /** (8) Replace getSizeInventory() body: call hookGetSizeInventory(this). */
    private void patchGetSizeInventory(MethodNode mn) {
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables.clear();

        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookGetSizeInventory",
                "(Ljava/lang/Object;)I", false));
        insn.add(new InsnNode(Opcodes.IRETURN));

        mn.instructions.add(insn);
        mn.maxStack  = 0;
        mn.maxLocals = 0;
        System.out.println("[GTNH Rocket Anim] getSizeInventory patched");
    }

    /**
     * Injects at the START of TileEntityFuelLoader.isCorrectFuel(IFuelable):
     *
     *   if (!RocketAnimHooks.hookFuelLoaderTierCheck(fuelable, this.fuelTank.getFluid()))
     *       return false;
     *   // ... original GC check follows ...
     *
     * Stack on entry: [this, fuelable]
     * We push fuelable (ALOAD_1), then this.fuelTank.getFluid() (ALOAD_0 + GETFIELD + INVOKEVIRTUAL),
     * call the hook, and branch.
     */
    private void injectFuelLoaderTierCheck(MethodNode mn) {
        InsnList guard = new InsnList();

        // arg: fuelable (slot 1 — first method parameter)
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));

        // arg: this.fuelTank.getFluid()
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, FUEL_LOADER, "fuelTank",
                "Lnet/minecraftforge/fluids/FluidTank;"));
        guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraftforge/fluids/FluidTank", "getFluid",
                "()Lnet/minecraftforge/fluids/FluidStack;", false));

        // call hook — returns boolean
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookFuelLoaderTierCheck",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));

        // if (hook returned true) jump past the early return
        LabelNode continueLabel = new LabelNode();
        guard.add(new JumpInsnNode(Opcodes.IFNE, continueLabel)); // IFNE = if non-zero (true)

        // hook returned false — block the fuel transfer
        guard.add(new InsnNode(Opcodes.ICONST_0));
        guard.add(new InsnNode(Opcodes.IRETURN));

        guard.add(continueLabel);

        // Insert at the very beginning of the method
        mn.instructions.insert(guard);
        System.out.println("[GTNH Rocket Anim] isCorrectFuel tier-check guard injected");
    }

    // ==========================================================================
    //  RenderCargoRocket patch implementations
    // ==========================================================================

    /**
     * (9a) In renderBuggy, find the invokeinterface IModelCustom.renderAll()V call
     * and replace it with hookRenderModel(model, entity).
     *
     * Before the invokeinterface the stack is: [..., model]
     * We insert ALOAD_1 (entity) to make it: [..., model, entity]
     * Then call hookRenderModel(Object model, Object entity)V instead.
     *
     * This keeps ALL of renderBuggy's GL transforms (translate/rotate/scale) intact
     * and just swaps the OBJ geometry for T3-T8.
     */
    private void patchRenderBuggyModelSwap(MethodNode mn) {
        for (AbstractInsnNode node : mn.instructions.toArray()) {
            if (node.getOpcode() == Opcodes.INVOKEINTERFACE) {
                MethodInsnNode min = (MethodInsnNode) node;
                if ("renderAll".equals(min.name) && "()V".equals(min.desc)) {
                    // Stack: [..., model]  →  insert entity  →  [..., model, entity]
                    mn.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, 1));
                    // Replace invokeinterface with invokestatic hookRenderModel
                    mn.instructions.set(node, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                            "hookRenderModel",
                            "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    System.out.println("[GTNH Rocket Anim] renderBuggy: renderAll() replaced with hookRenderModel");
                    return;
                }
            }
        }
        System.out.println("[GTNH Rocket Anim] renderBuggy: WARN — IModelCustom.renderAll() not found, model swap skipped");
    }

    private static final String RESOURCE_LOCATION_DESC = "Lnet/minecraft/util/ResourceLocation;";

    /**
     * (9) In renderBuggy, replace every GETSTATIC that pushes a ResourceLocation
     * with a call to hookGetCargoRocketTexture(entity).
     *
     * We intentionally match ANY static ResourceLocation field (not just the
     * well-known "cargoRocketTexture") so that GTNH fork variants with different
     * field names are also caught.  The entity argument is at the given local slot.
     */
    private void patchRenderTexture(MethodNode mn, int entitySlot) {
        int replaced = 0;
        for (AbstractInsnNode node : mn.instructions.toArray()) {
            if (node.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode fin = (FieldInsnNode) node;
                if (RESOURCE_LOCATION_DESC.equals(fin.desc)) {
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, entitySlot));
                    replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                            "hookGetCargoRocketTexture",
                            "(Ljava/lang/Object;)Lnet/minecraft/util/ResourceLocation;", false));
                    mn.instructions.insertBefore(node, replacement);
                    mn.instructions.remove(node);
                    replaced++;
                    System.out.println("[GTNH Rocket Anim] renderBuggy: replaced GETSTATIC " + fin.owner + "." + fin.name);
                }
            }
        }
        if (replaced == 0) {
            System.out.println("[GTNH Rocket Anim] renderBuggy: no static ResourceLocation field found " +
                               "(GC may use bindEntityTexture — func_110779_a patch handles it)");
        } else {
            System.out.println("[GTNH Rocket Anim] renderBuggy: replaced " + replaced + " ResourceLocation GETSTATIC(s)");
        }
    }

    /**
     * (10) Replace func_110779_a / getEntityTexture body:
     * just call hookGetCargoRocketTexture(ALOAD_1) and ARETURN.
     * The entity parameter is at slot 1 (slot 0 = this renderer).
     */
    private void patchGetEntityTexture(MethodNode mn) {
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables.clear();

        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1)); // entity arg
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                "hookGetCargoRocketTexture",
                "(Ljava/lang/Object;)Lnet/minecraft/util/ResourceLocation;", false));
        insn.add(new InsnNode(Opcodes.ARETURN));

        mn.instructions.add(insn);
        mn.maxStack  = 0;
        mn.maxLocals = 0;
        System.out.println("[GTNH Rocket Anim] func_110779_a / getEntityTexture patched");
    }

    // ==========================================================================
    //  Helpers
    // ==========================================================================

    /** Find the last RETURN opcode in a method. */
    private static AbstractInsnNode findLastReturn(MethodNode mn) {
        for (AbstractInsnNode n = mn.instructions.getLast(); n != null; n = n.getPrevious()) {
            if (n.getOpcode() == Opcodes.RETURN) return n;
        }
        return null;
    }

    private static void logMissing(String label, boolean patched) {
        if (!patched) {
            System.out.println("[GTNH Rocket Anim] WARNING: '" + label + "' not found — patch skipped.");
        }
    }
}

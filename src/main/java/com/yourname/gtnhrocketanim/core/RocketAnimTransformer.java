package com.yourname.gtnhrocketanim.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * ASM transformer for GTNH Galacticraft fork.
 *
 * Patches:
 *  1) EntityCargoRocket.moveToDestination(int) to actually respect its height parameter
 *     and route 800 -> configurable landingSpawnHeight.
 *  2) Injects a post-tick hook into EntityCargoRocket.func_70071_h_() so we can apply
 *     landing/takeoff easing without rewriting the mod's whole rocket logic.
 *  3) Injects a guard at the START of onReachAtmosphere() to delay teleport until
 *     takeoff animation completes.
 */
public class RocketAnimTransformer implements IClassTransformer {

    private static final String TARGET_CLASS_DOT =
            "micdoodle8.mods.galacticraft.planets.mars.entities.EntityCargoRocket";
    private static final String TARGET_CLASS =
            "micdoodle8/mods/galacticraft/planets/mars/entities/EntityCargoRocket";

    // Superclass where some fields live (landing, targetVec, launchPhase, timeSinceLaunch)
    private static final String AUTO_ROCKET_CLASS =
            "micdoodle8/mods/galacticraft/api/prefab/entity/EntityAutoRocket";

    private static final String BLOCKVEC3 = "micdoodle8/mods/galacticraft/api/vector/BlockVec3";

    private boolean patchedMoveToDestination = false;
    private boolean patchedTick = false;
    private boolean patchedOnReachAtmosphere = false;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if (!TARGET_CLASS_DOT.equals(name)) return basicClass;

        System.out.println("[GTNH Rocket Anim] Transforming " + name);

        ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        // Debug: print all method names to see what we're working with
        System.out.println("[GTNH Rocket Anim] Methods in class:");
        for (MethodNode mn : cn.methods) {
            if (mn.name.contains("tick") || mn.name.contains("update") || mn.name.contains("70071") || 
                mn.name.contains("Destination") || mn.name.equals("func_70071_h_") || mn.name.equals("onUpdate") ||
                mn.name.contains("Atmosphere") || mn.name.contains("onReach")) {
                System.out.println("[GTNH Rocket Anim]   - " + mn.name + mn.desc);
            }
        }

        for (MethodNode mn : cn.methods) {
            if ("moveToDestination".equals(mn.name) && "(I)V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching moveToDestination(I)V");
                patchMoveToDestination(mn);
                patchedMoveToDestination = true;
            }

            // Check for both obfuscated and deobfuscated names
            if ((mn.name.equals("func_70071_h_") || mn.name.equals("onUpdate")) && "()V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching " + mn.name + "()V (tick)");
                injectTickHook(mn);
                patchedTick = true;
            }
            
            // Patch onReachAtmosphere to delay teleport until takeoff animation complete
            if ("onReachAtmosphere".equals(mn.name) && "()V".equals(mn.desc)) {
                System.out.println("[GTNH Rocket Anim] Patching onReachAtmosphere()V");
                injectAtmosphereGuard(mn);
                patchedOnReachAtmosphere = true;
            }
        }

        if (!patchedMoveToDestination) {
            System.out.println("[GTNH Rocket Anim] WARNING: moveToDestination not found!");
        }
        if (!patchedTick) {
            System.out.println("[GTNH Rocket Anim] WARNING: func_70071_h_ not found!");
        }
        if (!patchedOnReachAtmosphere) {
            System.out.println("[GTNH Rocket Anim] WARNING: onReachAtmosphere not found!");
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private void patchMoveToDestination(MethodNode mn) {
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables.clear();

        InsnList insn = new InsnList();

        // *** CALL INTERCEPT HOOK FIRST ***
        // int result = RocketAnimHooks.interceptMoveToDestination(this, this.targetVec, this.destinationFrequency, arg1);
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (as Entity)
        
        // this.targetVec
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));
        
        // this.destinationFrequency
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "destinationFrequency", "I"));
        
        // arg1 (original height parameter)
        insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
        
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/yourname/gtnhrocketanim/RocketAnimHooks",
                "interceptMoveToDestination",
                "(Lnet/minecraft/entity/Entity;Ljava/lang/Object;II)I",
                false
        ));
        
        // Store result in a local variable (use slot 2 since slot 1 is the height arg)
        insn.add(new VarInsnNode(Opcodes.ISTORE, 2));
        
        // if (result == -1) return; // Abort - takeoff animation will handle teleport later
        insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
        insn.add(new InsnNode(Opcodes.ICONST_M1)); // -1
        LabelNode continueLabel = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IF_ICMPNE, continueLabel)); // if result != -1, continue
        insn.add(new InsnNode(Opcodes.RETURN)); // Abort!
        insn.add(continueLabel);
        
        // *** If we get here, proceed with immediate teleport (fallback behavior) ***
        // This code path is only used if the intercept hook returns the original height
        
        // Use result as the resolved height (stored in slot 2)
        // int resolved = result;

        // *** SET landing = true ***
        // this.landing = true;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new InsnNode(Opcodes.ICONST_1));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, AUTO_ROCKET_CLASS, "landing", "Z"));

        // *** ZERO OUT MOTION ***
        // In obfuscated runtime: motionX = field_70159_w, motionY = field_70181_x, motionZ = field_70179_y
        // this.motionX = 0;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new InsnNode(Opcodes.DCONST_0));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/entity/Entity", "field_70159_w", "D"));
        
        // this.motionY = 0;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new InsnNode(Opcodes.DCONST_0));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/entity/Entity", "field_70181_x", "D"));
        
        // this.motionZ = 0;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new InsnNode(Opcodes.DCONST_0));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/entity/Entity", "field_70179_y", "D"));

        // this.func_70107_b(targetVec.x + 0.5, targetVec.y + resolved + (destinationFrequency==1 ? 0 : 1), targetVec.z + 0.5)

        // this (invokevirtual owner)
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));

        // X = targetVec.x + 0.5 (targetVec is in EntityAutoRocket)
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "x", "I"));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new LdcInsnNode(0.5D));
        insn.add(new InsnNode(Opcodes.DADD));

        // Y = targetVec.y + resolved + (destinationFrequency == 1 ? 0 : 1)
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "y", "I"));
        insn.add(new InsnNode(Opcodes.I2D));

        // + resolved (slot 2, the result from intercept)
        insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new InsnNode(Opcodes.DADD));

        // + (destinationFrequency == 1 ? 0 : 1) - destinationFrequency is in EntityAutoRocket
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "destinationFrequency", "I"));
        insn.add(new InsnNode(Opcodes.ICONST_1));
        LabelNode eq = new LabelNode();
        LabelNode end = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, eq));
        insn.add(new InsnNode(Opcodes.DCONST_1));
        insn.add(new JumpInsnNode(Opcodes.GOTO, end));
        insn.add(eq);
        insn.add(new InsnNode(Opcodes.DCONST_0));
        insn.add(end);
        insn.add(new InsnNode(Opcodes.DADD));

        // Z = targetVec.z + 0.5
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, AUTO_ROCKET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, BLOCKVEC3, "z", "I"));
        insn.add(new InsnNode(Opcodes.I2D));
        insn.add(new LdcInsnNode(0.5D));
        insn.add(new InsnNode(Opcodes.DADD));

        // call setPosition (func_70107_b is Entity's setPosition)
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                TARGET_CLASS,
                "func_70107_b",
                "(DDD)V",
                false
        ));

        insn.add(new InsnNode(Opcodes.RETURN));

        mn.instructions.add(insn);
        mn.maxStack = 0;
        mn.maxLocals = 0;
        
        System.out.println("[GTNH Rocket Anim] Successfully patched moveToDestination (intercepts for takeoff animation)");
    }

    private void injectTickHook(MethodNode mn) {
        AbstractInsnNode ret = null;
        for (AbstractInsnNode n = mn.instructions.getLast(); n != null; n = n.getPrevious()) {
            if (n.getOpcode() == Opcodes.RETURN) { ret = n; break; }
        }
        if (ret == null) {
            System.out.println("[GTNH Rocket Anim] WARNING: No RETURN instruction found in tick method!");
            return;
        }

        InsnList call = new InsnList();

        // RocketAnimHooks.onCargoRocketTick((Entity)this, this.landing, this.targetVec, this.launchPhase, this.timeSinceLaunch);

        call.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this as Entity

        // Use TARGET_CLASS (EntityCargoRocket) as the owner - JVM will resolve to parent field
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "landing", "Z"));

        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "targetVec", "L" + BLOCKVEC3 + ";"));

        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "launchPhase", "I"));

        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, "timeSinceLaunch", "F"));

        call.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/yourname/gtnhrocketanim/RocketAnimHooks",
                "onCargoRocketTick",
                "(Lnet/minecraft/entity/Entity;ZLjava/lang/Object;IF)V",
                false
        ));

        mn.instructions.insertBefore(ret, call);
        System.out.println("[GTNH Rocket Anim] Successfully injected tick hook");
    }
    
    /**
     * Injects a guard at the START of onReachAtmosphere() that calls our hook.
     * If the hook returns true (delay requested), we return early from the method.
     * This prevents the teleport until the takeoff animation completes.
     */
    private void injectAtmosphereGuard(MethodNode mn) {
        InsnList guard = new InsnList();
        
        // if (RocketAnimHooks.shouldDelayAtmosphereTransition(this)) return;
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/yourname/gtnhrocketanim/RocketAnimHooks",
                "shouldDelayAtmosphereTransition",
                "(Lnet/minecraft/entity/Entity;)Z",
                false
        ));
        
        LabelNode continueLabel = new LabelNode();
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel)); // if false, continue
        guard.add(new InsnNode(Opcodes.RETURN)); // if true, return early
        guard.add(continueLabel);
        
        // Insert at the very beginning of the method
        mn.instructions.insert(guard);
        System.out.println("[GTNH Rocket Anim] Successfully injected atmosphere guard");
    }
}

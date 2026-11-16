var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var InsnList = Java.type("org.objectweb.asm.tree.InsnList");
var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");
var JumpInsnNode = Java.type("org.objectweb.asm.tree.JumpInsnNode");
var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");

var entity = "Lnet/minecraft/world/entity/Entity;";
var living = "Lnet/minecraft/world/entity/LivingEntity;";
var player = "Lnet/minecraft/server/level/ServerPlayer;";

function guard(owner, method, descriptor, helper, helperDescriptor, first, second, booleanResult) {
    return {
        target: {type: "METHOD", "class": owner, methodName: method, methodDesc: descriptor},
        transformer: function (methodNode) {
            var code = new InsnList();
            var resume = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, first));
            code.add(new VarInsnNode(Opcodes.ALOAD, second));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/madoka/magic/faction/FamiliarVisibility",
                helper, helperDescriptor, false));
            code.add(new JumpInsnNode(Opcodes.IFEQ, resume));
            if (booleanResult) {
                code.add(new InsnNode(Opcodes.ICONST_0));
                code.add(new InsnNode(Opcodes.IRETURN));
            } else {
                code.add(new InsnNode(Opcodes.RETURN));
            }
            code.add(resume);
            methodNode.instructions.insert(code);
            return methodNode;
        }
    };
}

function initializeCoreMod() {
    // Forge maps the 1.20.1 SRG names to the development namespace when needed.
    return {
        familiarTracking: guard("net.minecraft.world.entity.Entity", "m_6459_", "(" + player + ")Z",
            "blocksTracking", "(" + entity + player + ")Z", 0, 1, true),
        familiarTargeting: guard("net.minecraft.world.entity.ai.targeting.TargetingConditions",
            "m_26885_", "(" + living + living + ")Z", "ignoresTarget", "(" + entity + entity + ")Z", 1, 2, true),
        familiarPush: guard("net.minecraft.world.entity.Entity", "m_7334_", "(" + entity + ")V",
            "blocksPush", "(" + entity + entity + ")Z", 0, 1, false),
        familiarEffect: guard("net.minecraft.world.entity.LivingEntity", "m_147207_",
            "(Lnet/minecraft/world/effect/MobEffectInstance;" + entity + ")Z",
            "blocksEffect", "(" + entity + entity + ")Z", 0, 2, true)
    };
}

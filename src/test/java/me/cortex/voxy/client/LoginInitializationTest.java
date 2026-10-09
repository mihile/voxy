package me.cortex.voxy.client;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

/** Checks the injection point against actual compiled Minecraft code without launching a client. */
public class LoginInitializationTest {
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String GAME_MODE = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;";

    @Test
    public void sessionStartsImmediatelyAfterGameModeAssignment() throws IOException {
        MethodNode initializer = method(readClass("me/cortex/voxy/client/mixin/minecraft/MixinClientPacketListener"), "voxy$init");
        AnnotationNode inject = annotation(initializer.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        if (inject == null) {
            inject = annotation(initializer.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        }
        assertNotNull("The login initializer must remain a Mixin injector", inject);
        assertEquals(List.of("handleLogin"), value(inject, "method"));
        List<?> points = (List<?>) value(inject, "at");
        assertEquals("Only one login initialization point is allowed", 1, points.size());
        AnnotationNode point = (AnnotationNode) points.get(0);
        assertEquals("FIELD", value(point, "value"));
        assertEquals("L" + MINECRAFT + ";gameMode:" + GAME_MODE, value(point, "target"));
        assertEquals(Opcodes.PUTFIELD, value(point, "opcode"));
        assertArrayEquals(new String[]{"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"},
                (String[]) value(point, "shift"));
        long starts = 0;
        for (AbstractInsnNode instruction : initializer.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("me/cortex/voxy/client/ClientSessionEvents")
                    && call.name.equals("sessionStart")) starts++;
        }
        assertEquals("The injector must call sessionStart once", 1, starts);
    }

    @Test
    public void vanillaPreparesGameModeOnClientThreadBeforeRendererSetup() throws IOException {
        MethodNode login = method(readClass("net/minecraft/client/multiplayer/ClientPacketListener"), "handleLogin");
        int threadCheck = -1;
        int gameModeWrite = -1;
        int setLevel = -1;
        int writes = 0;
        int index = 0;
        for (AbstractInsnNode instruction : login.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/network/protocol/PacketUtils")
                        && call.name.equals("ensureRunningOnSameThread")) threadCheck = index;
                if (call.owner.equals(MINECRAFT) && call.name.equals("setLevel")) setLevel = index;
            }
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(MINECRAFT) && field.name.equals("gameMode") && field.desc.equals(GAME_MODE)) {
                gameModeWrite = index;
                writes++;
            }
            index++;
        }
        assertEquals("The injection must match exactly one gameMode assignment", 1, writes);
        assertTrue("Vanilla must check the thread before assigning gameMode",
                threadCheck >= 0 && threadCheck < gameModeWrite);
        assertTrue("Voxy must initialize before setLevel creates the renderer", gameModeWrite < setLevel);
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream stream = LoginInitializationTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IOException("Missing class: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        return annotations.stream().filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(name)) return annotation.values.get(index + 1);
        }
        throw new AssertionError("Missing annotation value: " + name);
    }
}

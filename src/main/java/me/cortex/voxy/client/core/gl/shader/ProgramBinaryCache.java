package me.cortex.voxy.client.core.gl.shader;

import me.cortex.voxy.common.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL41C.*;

/** Render-thread-only, bounded binary data cache. Owns no GL programs or world objects. */
public final class ProgramBinaryCache {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 64;
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("voxy.shaderBinaryCache", "true"));
    private record Binary(int format, byte[] bytes) {}
    private static final LinkedHashMap<String, Binary> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static GLCapabilities context;
    private static boolean supported;
    private static int retainedBytes;
    private static long hits, misses, rejected;

    private ProgramBinaryCache() {}

    public static String key(Map<ShaderType, String> sources) {
        if (!ENABLED) return null;
        GLCapabilities current = GL.getCapabilities();
        if (context != current) {
            // These are CPU byte arrays, so context changes require no GL deletion.
            clear();
            context = current;
            supported = current.glGetProgramBinary != 0 && current.glProgramBinary != 0
                    && current.glProgramParameteri != 0 && glGetInteger(GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
        }
        if (!supported) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ShaderType type : ShaderType.values()) {
                String source = sources.get(type);
                if (source == null) continue;
                byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                // Stage tag and byte length make the sequence unambiguous.
                digest.update((byte) type.ordinal());
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static int restore(String key) {
        if (key == null) return 0;
        Binary binary = CACHE.get(key);
        if (binary == null) {
            misses++;
            return 0;
        }
        ByteBuffer buffer = null;
        int program = 0;
        boolean restored = false;
        try {
            buffer = MemoryUtil.memAlloc(binary.bytes.length);
            buffer.put(binary.bytes).flip();
            program = glCreateProgram();
            glProgramBinary(program, binary.format, buffer);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE) {
                restored = true;
                hits++;
                return program;
            }
        } catch (RuntimeException e) {
            Logger.warn("Voxy shader binary restore failed; compiling source: " + e);
        } finally {
            if (buffer != null) MemoryUtil.memFree(buffer);
            if (!restored && program != 0) glDeleteProgram(program);
        }
        CACHE.remove(key);
        retainedBytes -= binary.bytes.length;
        misses++;
        rejected++;
        Logger.warn("Voxy shader binary rejected; compiling source");
        return 0;
    }

    public static void prepare(String key, int program) {
        if (key == null || !supported) return;
        try {
            glProgramParameteri(program, GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL_TRUE);
        } catch (RuntimeException e) {
            disableForContext("Voxy shader binary caching unavailable; compiling source: " + e);
        }
    }

    public static void store(String key, int program) {
        if (key == null || !supported || CACHE.containsKey(key)) return;
        ByteBuffer buffer = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int capacity = glGetProgrami(program, GL_PROGRAM_BINARY_LENGTH);
            if (capacity <= 0 || capacity > MAX_ENTRY_BYTES) return;
            buffer = MemoryUtil.memAlloc(capacity);
            var length = stack.callocInt(1);
            var format = stack.callocInt(1);
            glGetProgramBinary(program, length, format, buffer);
            int size = length.get(0);
            if (size <= 0 || size > capacity) return;
            while (!CACHE.isEmpty() && (CACHE.size() >= MAX_ENTRIES || retainedBytes + size > MAX_BYTES)) {
                var iterator = CACHE.entrySet().iterator();
                var eldest = iterator.next();
                retainedBytes -= eldest.getValue().bytes.length;
                iterator.remove();
            }
            byte[] bytes = new byte[size];
            buffer.get(bytes);
            CACHE.put(key, new Binary(format.get(0), bytes));
            retainedBytes += size;
        } catch (RuntimeException e) {
            // The newly linked program remains usable even if caching is unavailable.
            disableForContext("Voxy shader binary capture skipped: " + e);
        } finally {
            if (buffer != null) MemoryUtil.memFree(buffer);
        }
    }

    public static String statistics() {
        return "hits=" + hits + ", misses=" + misses + ", rejected=" + rejected
                + ", entries=" + CACHE.size() + ", bytes=" + retainedBytes;
    }

    private static void disableForContext(String message) {
        CACHE.clear();
        retainedBytes = 0;
        supported = false;
        Logger.warn(message);
    }

    public static void clear() {
        CACHE.clear();
        retainedBytes = 0;
        hits = misses = rejected = 0;
        context = null;
        supported = false;
    }
}

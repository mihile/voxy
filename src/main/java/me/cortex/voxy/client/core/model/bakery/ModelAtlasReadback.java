package me.cortex.voxy.client.core.model.bakery;

import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.common.Logger;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_PACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12C.GL_PACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.opengl.GL45C.*;

/** One in-flight atlas transfer. All GL operations run on the render thread. */
public final class ModelAtlasReadback {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("voxy.asyncModelAtlasReadback", "true"));
    private static final boolean PERSISTENT_MAPPING = Boolean.parseBoolean(
            System.getProperty("voxy.persistentModelAtlasReadback", "true"));
    private static final long MAX_BYTES = 256L << 20;
    private static final long FENCE_TIMEOUT_NANOS = 5_000_000_000L;
    private static final int[] PACK_PARAMETERS = {
            GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_PIXELS,
            GL_PACK_SKIP_ROWS, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_IMAGES
    };
    private static final int[] COMPACT_PACK = {1, 0, 0, 0, 0, 0};

    private final int[] pixels;
    private final long started = System.nanoTime();
    private GlBuffer buffer;
    private long fence;
    // GL owns the mapping, but the worker must not see its address until the fence signals.
    private long mappedAddress;
    private volatile long readableAddress;
    private volatile boolean copyDone;
    private volatile RuntimeException copyFailure;
    private long workerCopyNanos;
    private long mapNanos;
    private long preMapNanos;
    private long bufferAllocationNanos;
    private int polls;

    private ModelAtlasReadback(int[] pixels) {
        this.pixels = pixels;
    }

    public static ModelAtlasReadback tryBegin(int texture, int[] pixels) {
        long bytes = pixels.length * 4L;
        // FREX explicitly drains model work before completing a frame.
        if (!ENABLED || VoxyClient.isFrexActive() || bytes <= 0 || bytes > MAX_BYTES) return null;
        var result = new ModelAtlasReadback(pixels);
        try {
            int mappingFlags = GL_MAP_READ_BIT;
            int storageFlags = GL_MAP_READ_BIT;
            if (PERSISTENT_MAPPING) {
                mappingFlags |= GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
                storageFlags = mappingFlags | GL_CLIENT_STORAGE_BIT;
            }
            long phaseStarted = System.nanoTime();
            result.buffer = new GlBuffer(bytes, storageFlags, false).name("ModelAtlasReadback");
            result.bufferAllocationNanos = System.nanoTime() - phaseStarted;
            if (glGetNamedBufferParameteri64(result.buffer.id, GL_BUFFER_SIZE) != bytes) {
                throw new IllegalStateException("Atlas readback buffer allocation failed");
            }
            if (PERSISTENT_MAPPING) {
                // Map empty storage, before the GPU download. CLIENT_STORAGE is a
                // placement hint, not a guarantee that the driver uses host RAM.
                phaseStarted = System.nanoTime();
                result.mappedAddress = nglMapNamedBufferRange(result.buffer.id, 0L, bytes, mappingFlags);
                result.preMapNanos = System.nanoTime() - phaseStarted;
                if (result.mappedAddress == 0L) throw new IllegalStateException("Atlas pre-mapping returned null");
            }
            int previousBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
            int[] previousPack = capturePackState();
            try {
                compactPackState();
                glBindBuffer(GL_PIXEL_PACK_BUFFER, result.buffer.id);
                // With a pack buffer bound, zero is an offset, not a CPU pointer.
                nglGetTextureImage(texture, 0, GL_RGBA, GL_UNSIGNED_BYTE, (int) bytes, 0L);
                int error = glGetError();
                if (error != GL_NO_ERROR) throw new IllegalStateException("Atlas readback GL error " + error);
                result.fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                if (result.fence == 0) throw new IllegalStateException("Atlas readback fence creation failed");
                glFlush();
            } finally {
                restorePackState(previousPack);
                glBindBuffer(GL_PIXEL_PACK_BUFFER, previousBuffer);
            }
            Logger.info("Voxy model atlas transfer preparation: mapping=" + mappingMode()
                    + ", bytes=" + bytes + ", bufferAllocationMs=" + result.bufferAllocationNanos / 1_000_000.0
                    + ", preMapMs=" + result.preMapNanos / 1_000_000.0);
            return result;
        } catch (RuntimeException | Error e) {
            try { result.close(); } catch (RuntimeException | Error cleanup) { e.addSuppressed(cleanup); }
            if (e instanceof Error error) throw error;
            Logger.warn("Asynchronous model atlas readback unavailable; using synchronous readback", e);
            return null;
        }
    }

    /** Poll only; never asks GL to wait for an unfinished transfer. */
    public boolean pollOnRenderThread() {
        this.polls++;
        RuntimeException failure = this.copyFailure;
        if (failure != null) throw failure;
        if (this.readableAddress == 0L) {
            int status = glClientWaitSync(this.fence, 0, 0L);
            if (status == GL_TIMEOUT_EXPIRED) {
                if (System.nanoTime() - this.started > FENCE_TIMEOUT_NANOS) {
                    throw new IllegalStateException("Atlas readback fence did not complete within five seconds");
                }
                return false;
            }
            if (status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED) {
                throw new IllegalStateException("Atlas readback fence poll failed: " + status);
            }
            if (!PERSISTENT_MAPPING) {
                long beforeMap = System.nanoTime();
                this.mappedAddress = nglMapNamedBufferRange(this.buffer.id, 0L, this.buffer.size(), GL_MAP_READ_BIT);
                this.mapNanos = System.nanoTime() - beforeMap;
                if (this.mappedAddress == 0L) throw new IllegalStateException("Atlas readback mapping returned null");
            }
            // Coherent server writes are visible after the GPU fence signals.
            // Mapping early never permits the worker to read unfinished pixels.
            this.readableAddress = this.mappedAddress;
        }
        if (!this.copyDone) return false;
        // The worker has finished all native reads before copyDone is published.
        long beforeUnmap = System.nanoTime();
        boolean valid = glUnmapNamedBuffer(this.buffer.id);
        long unmapNanos = System.nanoTime() - beforeUnmap;
        this.mappedAddress = 0L;
        this.readableAddress = 0L;
        if (!valid) throw new IllegalStateException("Atlas readback mapping became invalid");
        long beforeRelease = System.nanoTime();
        this.close();
        long releaseNanos = System.nanoTime() - beforeRelease;
        Logger.info("Voxy model atlas readback ready: mode=pbo, bytes=" + this.pixels.length * 4L
                + ", mapping=" + mappingMode() + ", polls=" + this.polls
                + ", bufferAllocationMs=" + this.bufferAllocationNanos / 1_000_000.0
                + ", preMapMs=" + this.preMapNanos / 1_000_000.0
                + ", mapMs=" + this.mapNanos / 1_000_000.0
                + ", unmapMs=" + unmapNanos / 1_000_000.0
                + ", releaseMs=" + releaseNanos / 1_000_000.0
                + ", workerCopyMs=" + this.workerCopyNanos / 1_000_000.0
                + ", readyElapsedMs=" + (System.nanoTime() - this.started) / 1_000_000.0);
        return true;
    }

    /** CPU memory copy only, called by the single model worker. No GL context needed. */
    public void copyOnWorker() {
        if (this.copyDone || this.copyFailure != null) return;
        long address = this.readableAddress;
        if (address == 0L) return;
        try {
            long beforeCopy = System.nanoTime();
            MemoryUtil.memIntBuffer(address, this.pixels.length).get(this.pixels);
            this.workerCopyNanos = System.nanoTime() - beforeCopy;
            this.copyDone = true;
        } catch (RuntimeException e) {
            this.copyFailure = e;
        }
    }

    /** Render thread only. During shutdown the model worker must be joined first. */
    public void close() {
        var allocation = this.buffer;
        long sync = this.fence;
        boolean mapped = this.mappedAddress != 0L;
        this.buffer = null;
        this.fence = 0L;
        this.mappedAddress = 0L;
        this.readableAddress = 0L;
        try {
            if (mapped && allocation != null) glUnmapNamedBuffer(allocation.id);
        } finally {
            try {
                if (sync != 0L) glDeleteSync(sync);
            } finally {
                if (allocation != null) allocation.free();
            }
        }
    }

    public static void readSynchronously(int texture, int[] pixels) {
        int previousBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int[] previousPack = capturePackState();
        try {
            compactPackState();
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glGetTextureImage(texture, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        } finally {
            restorePackState(previousPack);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, previousBuffer);
        }
    }

    private static int[] capturePackState() {
        int[] values = new int[PACK_PARAMETERS.length];
        for (int i = 0; i < values.length; i++) values[i] = glGetInteger(PACK_PARAMETERS[i]);
        return values;
    }

    private static String mappingMode() {
        return PERSISTENT_MAPPING ? "persistent-coherent-client" : "after-fence";
    }

    private static void compactPackState() {
        restorePackState(COMPACT_PACK);
    }

    private static void restorePackState(int[] values) {
        for (int i = 0; i < values.length; i++) glPixelStorei(PACK_PARAMETERS[i], values[i]);
    }
}

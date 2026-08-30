package me.cortex.voxy.client.core.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.voxy.common.Logger;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

public final class AsyncTextureUploader {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("voxy.asyncTextureAnimationUploads", "true"));
    private static final ThreadLocal<Integer> SPRITE_UPLOAD_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static int pixelUnpackBuffer;
    private static boolean failed;
    private static boolean loggedEnabled;

    private AsyncTextureUploader() {}

    public static void beginSpriteUpload() {
        SPRITE_UPLOAD_DEPTH.set(SPRITE_UPLOAD_DEPTH.get() + 1);
    }

    public static void endSpriteUpload() {
        int depth = SPRITE_UPLOAD_DEPTH.get() - 1;
        if (depth <= 0) {
            SPRITE_UPLOAD_DEPTH.remove();
        } else {
            SPRITE_UPLOAD_DEPTH.set(depth);
        }
    }

    public static boolean upload(int target, int level, int xOffset, int yOffset,
                                 int uploadWidth, int uploadHeight, int glFormat, int glType,
                                 long sourcePixels, int sourceWidth, int sourceHeight, int components) {
        if (!ENABLED || failed || SPRITE_UPLOAD_DEPTH.get() == 0 || sourcePixels == 0L
                || uploadWidth <= 0 || uploadHeight <= 0 || components <= 0) {
            return false;
        }

        int previousBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int previousRowLength = GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH);
        int previousSkipPixels = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS);
        int previousSkipRows = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
        int previousAlignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);

        int sourceRowLength = previousRowLength == 0 ? sourceWidth : previousRowLength;
        if (previousSkipPixels < 0 || previousSkipRows < 0
                || previousSkipPixels + uploadWidth > sourceRowLength
                || previousSkipRows + uploadHeight > sourceHeight) {
            return false;
        }

        long rowBytes = Math.multiplyExact((long) uploadWidth, components);
        long uploadBytes = Math.multiplyExact(rowBytes, uploadHeight);
        boolean changedPixelStore = false;

        try {
            if (pixelUnpackBuffer == 0) {
                pixelUnpackBuffer = GL15C.glGenBuffers();
            }

            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
            // Re-specifying the store orphans any storage still consumed by an older upload.
            GL15C.glBufferData(GL21C.GL_PIXEL_UNPACK_BUFFER, uploadBytes, GL15C.GL_STREAM_DRAW);

            long mapped = GL30C.nglMapBufferRange(GL21C.GL_PIXEL_UNPACK_BUFFER, 0L, uploadBytes,
                    GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_INVALIDATE_BUFFER_BIT
                            | GL30C.GL_MAP_UNSYNCHRONIZED_BIT);
            if (mapped == 0L) {
                disableAfterFailure("pixel unpack buffer mapping returned null", null);
                return false;
            }

            long sourceStride = Math.multiplyExact((long) sourceRowLength, components);
            long sourceStart = sourcePixels
                    + Math.multiplyExact((long) previousSkipRows, sourceStride)
                    + Math.multiplyExact((long) previousSkipPixels, components);
            for (int row = 0; row < uploadHeight; row++) {
                MemoryUtil.memCopy(sourceStart + row * sourceStride, mapped + row * rowBytes, rowBytes);
            }

            if (!GL15C.glUnmapBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER)) {
                disableAfterFailure("pixel unpack buffer contents became invalid", null);
                return false;
            }

            GlStateManager._pixelStore(GL11C.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11C.GL_UNPACK_ALIGNMENT, 1);
            changedPixelStore = true;

            // With a pixel unpack buffer bound, zero is a buffer offset instead of a CPU pointer.
            GlStateManager._texSubImage2D(target, level, xOffset, yOffset,
                    uploadWidth, uploadHeight, glFormat, glType, 0L);

            if (!loggedEnabled) {
                loggedEnabled = true;
                Logger.info("Enabled asynchronous animated texture uploads via pixel unpack buffer");
            }
            return true;
        } catch (Throwable throwable) {
            disableAfterFailure("asynchronous animated texture upload failed", throwable);
            return false;
        } finally {
            if (changedPixelStore) {
                GlStateManager._pixelStore(GL11C.GL_UNPACK_ROW_LENGTH, previousRowLength);
                GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
                GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_ROWS, previousSkipRows);
                GlStateManager._pixelStore(GL11C.GL_UNPACK_ALIGNMENT, previousAlignment);
            }
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, previousBuffer);
        }
    }

    private static void disableAfterFailure(String message, Throwable throwable) {
        if (!failed) {
            failed = true;
            Logger.warn(message, "Falling back to Minecraft's normal texture upload path.", throwable);
        }
    }
}

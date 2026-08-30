package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import me.cortex.voxy.client.core.gl.AsyncTextureUploader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NativeImage.class)
public abstract class MixinNativeImageAsyncUpload {
    @Shadow
    private long pixels;

    @Shadow
    @Final
    private int width;

    @Shadow
    @Final
    private int height;

    @Shadow
    @Final
    private NativeImage.Format format;

    @Redirect(
            method = "_upload",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texSubImage2D(IIIIIIIIJ)V",
                    remap = false))
    private void voxy$uploadThroughPixelBuffer(int target, int level, int xOffset, int yOffset,
                                               int uploadWidth, int uploadHeight,
                                               int glFormat, int glType, long sourcePixels) {
        if (!AsyncTextureUploader.upload(target, level, xOffset, yOffset,
                uploadWidth, uploadHeight, glFormat, glType,
                sourcePixels, this.width, this.height, this.format.components())) {
            GlStateManager._texSubImage2D(target, level, xOffset, yOffset,
                    uploadWidth, uploadHeight, glFormat, glType, sourcePixels);
        }
    }
}

package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.platform.NativeImage;
import me.cortex.voxy.client.core.gl.AsyncTextureUploader;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpriteContents.class)
public abstract class MixinSpriteContentsAsyncUpload {
    @Redirect(
            method = "upload",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZZ)V"))
    private void voxy$markSpriteUpload(NativeImage image, int level, int xOffset, int yOffset,
                                       int unpackSkipPixels, int unpackSkipRows,
                                       int width, int height, boolean blur, boolean close) {
        AsyncTextureUploader.beginSpriteUpload();
        try {
            image.upload(level, xOffset, yOffset, unpackSkipPixels, unpackSkipRows,
                    width, height, blur, close);
        } finally {
            AsyncTextureUploader.endSpriteUpload();
        }
    }
}

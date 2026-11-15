package me.cortex.voxy.client.mixin.sodium;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.ARGB;
import me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {CloudRenderer.class}, remap = false, priority = 1100)
public class MixinCloudRenderer {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private int voxy$cloudRenderDistanceRedirect(int a, int b) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) {
            return Math.max(a, b);
        }
        if (VoxyConfig.CONFIG.adaptCloudDistance) {
            int base = Math.max(a, b);
            return ARGB.clamp((int) (VoxyConfig.CONFIG.sectionRenderDistance * 32F) + 9, base, 256);
        }
        return VoxyConfig.CONFIG.cloudDistance < 1 ? Math.max(a, b) : VoxyConfig.CONFIG.cloudDistance + 9;
    }
}

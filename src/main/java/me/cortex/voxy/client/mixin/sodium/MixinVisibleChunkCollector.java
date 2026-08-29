package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.5.x equivalent of current upstream Voxy's visible-section collector
 * integration. Feeding only the sections accepted by Sodium's occlusion culler
 * produces an exact per-frame depth bound instead of the old all-loaded-chunks
 * approximation.
 */
@Mixin(value = VisibleChunkCollector.class, remap = false)
public class MixinVisibleChunkCollector {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetVisibleStream(int frame, CallbackInfo ci) {
        VoxyRenderSystem vrs = IGetVoxyRenderSystem.getNullable();
        if (vrs != null) vrs.visibleSectionStream.reset();
    }

    @Inject(method = "visit", at = @At("HEAD"))
    private void voxy$captureVisibleSection(RenderSection section, boolean visible, CallbackInfo ci) {
        if (!visible || section.getFlags() == 0 || IrisUtil.irisShadowActive()) return;

        VoxyRenderSystem vrs = IGetVoxyRenderSystem.getNullable();
        if (vrs != null) {
            vrs.visibleSectionStream.put(SectionPos.asLong(
                    section.getChunkX(), section.getChunkY(), section.getChunkZ()));
        }
    }
}

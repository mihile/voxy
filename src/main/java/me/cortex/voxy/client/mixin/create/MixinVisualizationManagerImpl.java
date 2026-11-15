package me.cortex.voxy.client.mixin.create;

import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import me.cortex.voxy.client.compat.create.DistantKineticRenderer;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VisualizationManagerImpl.class, remap = false)
public final class MixinVisualizationManagerImpl {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true)
    private static void voxy$captureLegacyRenderer(LevelAccessor level, CallbackInfoReturnable<Boolean> cir) {
        if (DistantKineticRenderer.isCapturing()) cir.setReturnValue(false);
    }
}

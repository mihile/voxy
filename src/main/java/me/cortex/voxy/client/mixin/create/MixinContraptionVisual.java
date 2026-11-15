package me.cortex.voxy.client.mixin.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import me.cortex.voxy.client.compat.create.DistantCreateRenderer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContraptionVisual.class, remap = false)
public class MixinContraptionVisual {
    private static final Matrix4f VOXY$ZERO_POSE = new Matrix4f().scaling(0.0f);
    private static final Matrix3f VOXY$ZERO_NORMAL = new Matrix3f().scaling(0.0f);
    @Shadow @Final protected VisualEmbedding embedding;

    @Inject(method = "beginFrame", at = @At("TAIL"))
    private void voxy$handover(DynamicVisual.Context context, CallbackInfo ci) {
        var entity = (AbstractContraptionEntity) ((AccessorAbstractEntityVisual) this).voxy$getEntity();
        if (DistantCreateRenderer.shouldCullLive(entity,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
            this.embedding.transforms(VOXY$ZERO_POSE, VOXY$ZERO_NORMAL);
        }
    }
}

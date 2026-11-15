package me.cortex.voxy.client.mixin.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import me.cortex.voxy.client.compat.create.DistantCreateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public class MixinContraptionEntityRenderer {
    @Inject(method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void voxy$handover(AbstractContraptionEntity entity, float yaw, float partialTicks,
                               PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (DistantCreateRenderer.shouldCullLive(entity,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
            ci.cancel();
        }
    }
}

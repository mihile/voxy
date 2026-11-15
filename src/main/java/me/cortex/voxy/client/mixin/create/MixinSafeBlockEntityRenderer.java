package me.cortex.voxy.client.mixin.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import me.cortex.voxy.client.compat.create.DistantKineticRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SafeBlockEntityRenderer.class)
public class MixinSafeBlockEntityRenderer {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void voxy$handover(BlockEntity be, float partialTicks, PoseStack pose,
                               MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (DistantKineticRenderer.isCapturing()) return;
        if (be instanceof KineticBlockEntity
                && be.getLevel() == Minecraft.getInstance().level
                && DistantKineticRenderer.shouldCullLive(be.getBlockPos(),
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
            ci.cancel();
        }
    }
}

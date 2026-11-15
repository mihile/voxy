package me.cortex.voxy.client.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if forge
import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRendererVS {
//? if forge {
    @Inject(method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V", at = @At("TAIL"))
    private void injectRender(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, CallbackInfo ci) {
        this.doRender(ChunkRenderMatrices.from(matrixStack), renderLayer, x, y, z);
    }
//? } else {
    /*@Inject(method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDD)V", at = @At("TAIL"))
    private void injectRender(RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
        this.doRender(matrices, renderLayer, x, y, z);
    }
*///? }
    @Unique
    private void doRender(ChunkRenderMatrices matrices, RenderType renderLayer, double x, double y, double z) {
        if (IrisUtil.irisShadowActive()) {
            return;
        }
        if (renderLayer == RenderType.solid()) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                Viewport<?> viewport;
                if (IrisUtil.irisShaderPackEnabled()) {
                    viewport = renderer.getViewport();
                } else {
                    viewport = renderer.setupViewport(
                            matrices.projection(), matrices.modelView(), x, y, z);
                }
                renderer.renderOpaque(viewport);
            }
        }
    }
}

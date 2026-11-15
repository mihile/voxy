package me.cortex.voxy.client.mixin.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor
    RenderTargets getRenderTargets();

    @Accessor
    ShaderPack getPack();

    @Accessor
    PackDirectives getPackDirectives();

    @Accessor
    FrameUpdateNotifier getUpdateNotifier();
}

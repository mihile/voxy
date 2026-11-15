package me.cortex.voxy.client.mixin.iris;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(value = CustomUniforms.class, remap = false)
public class MixinCustomUniforms {
    @Shadow @Final private List<CachedUniform> uniforms;
    @Shadow @Final private Map<Object, Object2IntMap<CachedUniform>> locationMap;
    @Unique private final Object voxy$retainedUniformPass = new Object();

    @Inject(method = "optimise", at = @At("HEAD"))
    private void voxy$retainLodOnlyUniforms(CallbackInfo ci) {
        if (!IrisUtil.SHADER_SUPPORT) {
            return;
        }

        Object2IntMap<CachedUniform> retained = new Object2IntOpenHashMap<>();
        var requestedUniforms = IrisShaderPatch.uniformsBeingBuilt();
        if (requestedUniforms.isEmpty()) {
            return;
        }
        for (CachedUniform uniform : this.uniforms) {
            if (requestedUniforms.contains(uniform.getName())) {
                retained.put(uniform, 0);
            }
        }
        if (!retained.isEmpty()) {
            this.locationMap.put(this.voxy$retainedUniformPass, retained);
        }
    }

    @Inject(method = "optimise", at = @At("RETURN"))
    private void voxy$removeRetentionMarker(CallbackInfo ci) {
        this.locationMap.remove(this.voxy$retainedUniformPass);
    }
}

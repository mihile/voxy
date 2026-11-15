package me.cortex.voxy.client.compat.distant;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;

public final class DistantBeamShader {
    private static Shader shader;
    private DistantBeamShader() {}

    public static Shader get() {
        if (shader == null) {
            shader = Shader.make().add(ShaderType.VERTEX, "voxy:compat/distant_beam.vert")
                    .add(ShaderType.FRAGMENT, "voxy:compat/distant_beam.frag")
                    .compile().name("distant_beam");
        }
        return shader;
    }

    public static void bindTextures(ResourceLocation texture) {
        glBindTextureUnit(0, Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
        glBindSampler(0, 0);
        LightMapHelper.bind(1);
    }

    public static void uploadTransform(Matrix4f transform) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = stack.mallocFloat(16);
            transform.get(data);
            nglUniformMatrix4fv(0, 1, false, org.lwjgl.system.MemoryUtil.memAddress(data));
        }
    }
}

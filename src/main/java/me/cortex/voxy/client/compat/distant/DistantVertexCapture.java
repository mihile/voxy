package me.cortex.voxy.client.compat.distant;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class DistantVertexCapture implements MultiBufferSource, VertexConsumer {
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    };

    private final DistantMesh.Builder builder;
    private final boolean chunkLayersOnly;
    private final int defaultBlockLight;
    private final int defaultSkyLight;
    private double x;
    private double y;
    private double z;
    private float u;
    private float v;
    private float nx;
    private float ny;
    private float nz;
    private int r;
    private int g;
    private int b;
    private int a;
    private int blockLight;
    private int skyLight;
    private boolean defaultColor;
    private int defaultR;
    private int defaultG;
    private int defaultB;
    private int defaultA;

    public DistantVertexCapture(DistantMesh.Builder builder, boolean chunkLayersOnly) {
        this(builder, chunkLayersOnly, 0, 240);
    }

    public DistantVertexCapture(DistantMesh.Builder builder, boolean chunkLayersOnly,
                                int defaultBlockLight, int defaultSkyLight) {
        this.builder = builder;
        this.chunkLayersOnly = chunkLayersOnly;
        this.defaultBlockLight = centerLight(defaultBlockLight);
        this.defaultSkyLight = centerLight(defaultSkyLight);
        this.reset();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (!this.chunkLayersOnly) return this;
        return RenderType.chunkBufferLayers().contains(type) ? this : DISCARD;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        this.r = r & 255;
        this.g = g & 255;
        this.b = b & 255;
        this.a = a & 255;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.blockLight = centerLight(u);
        this.skyLight = centerLight(v);
        return this;
    }

    private static int centerLight(int value) {
        return Math.min(255, Math.max(0, value) + 8);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.nx = x;
        this.ny = y;
        this.nz = z;
        return this;
    }

    @Override
    public void endVertex() {
        int face;
        if (this.ny > 0.6f) face = 1;
        else if (this.ny < -0.6f) face = 0;
        else if (Math.abs(this.nz) > Math.abs(this.nx)) face = this.nz > 0 ? 3 : 2;
        else face = this.nx > 0 ? 5 : 4;
        int rgb = (this.r << 16) | (this.g << 8) | this.b;
        this.builder.vertex((float) this.x, (float) this.y, (float) this.z, this.u, this.v,
                rgb, this.a, this.blockLight, this.skyLight, face);
        this.reset();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        this.defaultColor = true;
        this.defaultR = r & 255;
        this.defaultG = g & 255;
        this.defaultB = b & 255;
        this.defaultA = a & 255;
        this.r = this.defaultR;
        this.g = this.defaultG;
        this.b = this.defaultB;
        this.a = this.defaultA;
    }

    @Override
    public void unsetDefaultColor() {
        this.defaultColor = false;
    }

    private void reset() {
        this.u = 0;
        this.v = 0;
        this.nx = 0;
        this.ny = 1;
        this.nz = 0;
        this.blockLight = this.defaultBlockLight;
        this.skyLight = this.defaultSkyLight;
        if (this.defaultColor) {
            this.r = this.defaultR;
            this.g = this.defaultG;
            this.b = this.defaultB;
            this.a = this.defaultA;
        } else {
            this.r = this.g = this.b = this.a = 255;
        }
    }
}

package me.cortex.voxy.client.mixin.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.cortex.voxy.client.ICheekyClientChunkCache;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
// import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkStatus;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Unique
    private static final boolean BOBBY_INSTALLED = FabricLoader.getInstance().isModLoaded("bobby");

    @Shadow @Final private ClientLevel world;

    @Shadow @Final private ChunkBuilder builder;

    /**
     * Chunks which were already ingested successfully by {@code onChunkAdded}.
     * Sodium subsequently uploads each non-empty section in the same chunk; without
     * this guard those uploads enqueue the same voxelization work a second time.
     */
    @Unique
    private final LongSet voxy$fullyIngestedChunks = new LongOpenHashSet();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkTracker(ClientLevel level, int renderDistance, CommandList commandList, CallbackInfo ci) {
        if (level.levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(level.levelRenderer)).voxy$getRenderSystem();
            if (system != null) system.visibleSectionStream.reset();
        }
        this.bottomSectionY = this.world.getMinBuildHeight()>>4;
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$injectIngest(int x, int z, CallbackInfo ci) {
        this.voxy$fullyIngestedChunks.remove(ChunkPos.asLong(x, z));
        //TODO: Am not quite sure if this is right
        if (VoxyConfig.CONFIG.ingestEnabled && !BOBBY_INSTALLED) {
            var cccm = (ICheekyClientChunkCache)this.world.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.voxy$cheekyGetChunk(x, z);
                if (chunk != null) {
                    VoxelIngestService.tryAutoIngestChunk(chunk);
                }
            }
        }
    }


    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$ingestOnAdd(int x, int z, CallbackInfo ci) {
        if (this.world.levelRenderer != null && VoxyConfig.CONFIG.ingestEnabled) {
            var cccm = this.world.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    long chunkPos = ChunkPos.asLong(x, z);
                    if (VoxelIngestService.tryAutoIngestChunk(chunk)) {
                        this.voxy$fullyIngestedChunks.add(chunkPos);
                    } else {
                        // Lighting is commonly unavailable at this point. Keep the
                        // per-section upload fallback enabled for this chunk.
                        this.voxy$fullyIngestedChunks.remove(chunkPos);
                    }
                }
            }
        }
    }

    /*
    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$trackChunkRemove(int x, int z, CallbackInfo ci) {
        if (this.world.worldRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.world.worldRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.removeSection(ChunkPos.toLong(x, z));
            }
        }
    }*/

    @Unique private long cachedChunkPos = -1;
    @Unique private int cachedChunkStatus;
    @Unique private int bottomSectionY;

    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;setInfo(Lme/jellysquid/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)V"))
    private void voxy$updateOnUpload(RenderSection instance, BuiltSectionInfo info) {
        boolean wasBuilt = instance.getFlags()!=0;
        int flags = instance.getFlags();
        instance.setInfo(info);
        if (wasBuilt == (instance.getFlags()!=0)) {//Only want to do stuff on change
            return;
        }

        flags |= instance.getFlags();
        if (flags == 0)//Only process things with stuff
            return;

        VoxyRenderSystem system = ((IGetVoxyRenderSystem)(this.world.levelRenderer)).voxy$getRenderSystem();
        if (system == null) {
            return;
        }
        int x = instance.getChunkX(), y = instance.getChunkY(), z = instance.getChunkZ();

        if (wasBuilt && VoxyConfig.CONFIG.ingestEnabled) {
            var tracker = ((AccessorChunkTracker)ChunkTrackerHolder.get(this.world)).getChunkStatus();
            //in theory the cache value could be wrong but is so soso unlikely and at worst means we either duplicate ingest a chunk
            // which... could be bad ;-; or we dont ingest atall which is ok!
            long key = ChunkPos.asLong(x, z);
            if (key != this.cachedChunkPos) {
                this.cachedChunkPos = key;
                this.cachedChunkStatus = tracker.getOrDefault(key, 0);
            }
            if (this.cachedChunkStatus == 3) {//If this chunk still has surrounding chunks
                var section = this.world.getChunk(x,z).getSection(y-this.bottomSectionY);
                var lp = this.world.getLightEngine();

                var csp = SectionPos.of(x, y, z);
                var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                //Note: we dont do this check and just blindly ingest, it shouldbe ok :tm:
                //if (blp != null || slp != null)
                    VoxelIngestService.rawIngest(system.getEngine(), section, x, y, z, blp == null ? null : blp.copy(), slp == null ? null : slp.copy());
            }
        }
        

        boolean alreadyFullyIngested = this.voxy$fullyIngestedChunks.contains(ChunkPos.asLong(x, z));

        //Do some very cheeky stuff for MiB
        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (x+512)>>10;
            x-=sector<<10;
            y+=16+(256-32-sector*30);
        }
        if (!wasBuilt) {//Add — chunk newly built for the first time
            // FIX: ingest newly explored chunks here, where Sodium has already compiled
            // the geometry and lighting is guaranteed to be available. The onChunkAdded
            // hook fires too early (before lighting arrives for freshly generated chunks).
            if (VoxyConfig.CONFIG.ingestEnabled && !alreadyFullyIngested) {
                var tracker = ((AccessorChunkTracker)ChunkTrackerHolder.get(this.world)).getChunkStatus();
                long key = ChunkPos.asLong(x, z);
                if (key != this.cachedChunkPos) {
                    this.cachedChunkPos = key;
                    this.cachedChunkStatus = tracker.getOrDefault(key, 0);
                }
                // Status 3 = chunk has all surrounding neighbours loaded (LIGHT_AND_BIOMES),
                // so lighting is complete and safe to read.
                if (this.cachedChunkStatus == 3) {
                    var section = this.world.getChunk(x, z).getSection(y - this.bottomSectionY);
                    var lp = this.world.getLightEngine();
                    var csp = SectionPos.of(x, y, z);
                    var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                    var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);
                    VoxelIngestService.rawIngest(system.getEngine(), section, x, y, z,
                            blp == null ? null : blp.copy(),
                            slp == null ? null : slp.copy());
                }
            }
        }
        return;
    }
}

package me.cortex.voxy.client.mixin.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.cortex.voxy.client.ICheekyClientChunkCache;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
//? if 1.21.1
//import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
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
    @Shadow(aliases = "world") @Final private ClientLevel level;

    @Shadow @Final private ChunkBuilder builder;

    /**
     * Chunks already fully ingested on chunk add; avoids duplicate per-section
     * voxelization work during initial Embeddium section uploads.
     */
    @Unique
    private final LongSet voxy$fullyIngestedChunks = new LongOpenHashSet();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkTracker(
        ClientLevel level,
        int renderDistance,
        //? if 1.21.1
        //SortBehavior sortBehavior,
        CommandList commandList,
        CallbackInfo ci
    ) {
        if (level.levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(level.levelRenderer)).voxy$getRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.reset();
            }
        }
        this.bottomSectionY = this.level.getMinBuildHeight()>>4;
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$injectIngest(int x, int z, CallbackInfo ci) {
        this.voxy$fullyIngestedChunks.remove(ChunkPos.asLong(x, z));
        //TODO: Am not quite sure if this is right
        if (VoxyConfig.CONFIG.ingestEnabled && !VoxyCommon.getPlatformUtil().isModLoaded("bobby")) {
            var cccm = (ICheekyClientChunkCache)this.level.getChunkSource();
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
        if (this.level.levelRenderer != null && VoxyConfig.CONFIG.ingestEnabled) {
            var cccm = this.level.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    long chunkPos = ChunkPos.asLong(x, z);
                    if (VoxelIngestService.tryAutoIngestChunk(chunk)) {
                        this.voxy$fullyIngestedChunks.add(chunkPos);
                    } else {
                        this.voxy$fullyIngestedChunks.remove(chunkPos);
                    }
                }
            }
        }
    }

    /*
    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$trackChunkRemove(int x, int z, CallbackInfo ci) {
        if (this.level.worldRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.level.worldRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.removeSection(ChunkPos.toLong(x, z));
            }
        }
    }*/

    @Unique private long cachedChunkPos = -1;
    @Unique private int cachedChunkStatus;
    @Unique private int bottomSectionY;

    // Single Stonecutter-driven redirect: annotation target, return type, and final return are generated
    // based on the `1.21.1` flag so we only keep one copy of the body.
    //? if 1.21.1 {
    /*@Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;setInfo(Lme/jellysquid/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z"))
    *///? } else {
    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;setInfo(Lme/jellysquid/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)V"))
    //? }
    //? if 1.21.1 {
    /*private boolean
    *///? } else {
    private void
    //? }
    voxy$updateOnUpload(RenderSection instance, BuiltSectionInfo info) {
        boolean wasBuilt = instance.getFlags() != 0;
        int flags = instance.getFlags();
        instance.setInfo(info);
        if (wasBuilt == (instance.getFlags() != 0)) { // Only want to do stuff on change
            //? if 1.21.1 {
            /*return true;
            *///? } else {
            return;
            //? }
        }

        flags |= instance.getFlags();
        if (flags == 0) // Only process things with stuff
            //? if 1.21.1 {
            /*return true;
            *///? } else {
            return;
            //? }

        VoxyRenderSystem system = ((IGetVoxyRenderSystem) (this.level.levelRenderer)).voxy$getRenderSystem();
        if (system == null) {
            //? if 1.21.1 {
            /*return true;
            *///? } else {
            return;
            //? }
        }
        int x = instance.getChunkX(), y = instance.getChunkY(), z = instance.getChunkZ();

        if (wasBuilt && VoxyConfig.CONFIG.ingestEnabled) {
            var tracker = ((AccessorChunkTracker) ChunkTrackerHolder.get(this.level)).getChunkStatus();
            //in theory the cache value could be wrong but is so soso unlikely and at worst means we either duplicate ingest a chunk
            // which... could be bad ;-; or we dont ingest atall which is ok!
            long key = ChunkPos.asLong(x, z);
            if (key != this.cachedChunkPos) {
                this.cachedChunkPos = key;
                this.cachedChunkStatus = tracker.getOrDefault(key, 0);
            }
            if (this.cachedChunkStatus == 3) { //If this chunk still has surrounding chunks
                var cccm = this.level.getChunkSource();
                //var chunk = ((ICheekyClientChunkCache)cccm).voxy$cheekyGetChunk(x, z);
                //Dont thinks need to use cheekyGetChunk here as thats handled by the inject into head of onChunkRemoved
                // but only ingest if the chunkstatus is full and exists
                var chunk = cccm.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    var section = chunk.getSection(y - this.bottomSectionY);
                    var lp = this.level.getLightEngine();

                    var csp = SectionPos.of(x, y, z);
                    var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                    var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                    //Note: we dont do this check and just blindly ingest, it shouldbe ok :tm:
                    //if (blp != null || slp != null)
                    VoxelIngestService.rawIngest(system.getEngine(), section, x, y, z, blp == null ? null : blp.copy(), slp == null ? null : slp.copy());
                }
            }
        }

        long chunkKey = ChunkPos.asLong(x, z);
        boolean alreadyFullyIngested = this.voxy$fullyIngestedChunks.contains(chunkKey);

        //Do some very cheeky stuff for MiB
        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (x+512)>>10;
            x-=sector<<10;
            y+=16+(256-32-sector*30);
        }
        long pos = SectionPos.asLong(x,y,z);
        if (wasBuilt) {//Remove
            system.chunkBoundRenderer.removeSection(pos);
        } else {//Add
            if (VoxyConfig.CONFIG.ingestEnabled && !alreadyFullyIngested) {
                var tracker = ((AccessorChunkTracker) ChunkTrackerHolder.get(this.level)).getChunkStatus();
                if (chunkKey != this.cachedChunkPos) {
                    this.cachedChunkPos = chunkKey;
                    this.cachedChunkStatus = tracker.getOrDefault(chunkKey, 0);
                }
                if (this.cachedChunkStatus == 3) {
                    var cccm = this.level.getChunkSource();
                    var chunk = cccm == null ? null : cccm.getChunk(x, z, ChunkStatus.FULL, false);
                    if (chunk != null) {
                        var section = chunk.getSection(y - this.bottomSectionY);
                        var lp = this.level.getLightEngine();
                        var csp = SectionPos.of(x, y, z);
                        var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                        var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);
                        VoxelIngestService.rawIngest(system.getEngine(), section, x, y, z,
                                blp == null ? null : blp.copy(),
                                slp == null ? null : slp.copy());
                    }
                }
            }
            system.chunkBoundRenderer.addSection(pos);
        }
        //? if 1.21.1 {
        /*return true;
        *///? } else {
        return;
        //? }
    }
}

package me.cortex.voxy.common.platform;

//? if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
*///? } else if forge {
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.api.distmarker.Dist;
//?} else {
/*import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.compat.IFlashbackMeta;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
*///?}

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class PlatformUtilImpl implements PlatformUtil {
    @Override
    public boolean isModLoaded(String modId) {
        //? if forge || neoforge {
        LoadingModList lm = LoadingModList.get();
        if (lm == null) return false;
        return lm.getModFileById(modId) != null;
        //? } else {
        /*return FabricLoader.getInstance().isModLoaded(modId);
        *///? }
    }

    @Override
    public Path getModRootPath(String modId) {
        //? if forge || neoforge {
        LoadingModList lm = LoadingModList.get();
        if (lm == null) return null;
        ModFileInfo info = lm.getModFileById(modId);
        if (info == null) return null;

        return info.getFile().getSecureJar().getRootPath();
        //? } else {
        /*return FabricLoader.getInstance().getModContainer(modId).get().getRootPaths().get(0);
        *///? }
    }

    @Override
    public String getModVersion(String modId) {
        //? if forge || neoforge {
        LoadingModList lm = LoadingModList.get();
        if (lm == null) return null;
        ModFileInfo info = lm.getModFileById(modId);
        if (info == null) return null;

        return info.versionString();
        //? } else {
        /*return FabricLoader.getInstance().getModContainer(modId).get().getMetadata().getVersion().getFriendlyString();
        *///? }
    }

    @Override
    public Path getReplayStoragePath(boolean flashbackInstalled) {
        //? if fabric {
        /*if (!flashbackInstalled) {
            return null;
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            FlashbackMeta meta = replayServer.getMetadata();
            if (meta != null) {
                var path = ((IFlashbackMeta)meta).getVoxyPath();
                if (path != null) {
                    Logger.info("Flashback replay server exists and meta exists");
                    if (path.exists()) {
                        Logger.info("Flashback voxy path exists in filesystem, using this as lod data source");
                        return path.toPath();
                    } else {
                        Logger.warn("Flashback meta had voxy path saved but path doesnt exist");
                    }
                }
            }
        }
        return null;
        *///? } else {
        return null;
        //? }
    }

    @Override
    public Path getConfigDir() {
        //? if forge || neoforge {    
        return FMLPaths.CONFIGDIR.get();
        //? } else {
        /*return FabricLoader.getInstance().getConfigDir();
        *///? }
    }

    @Override
    public boolean isDedicatedServer() {
        //? if forge || neoforge {
        return FMLLoader.getDist() == Dist.DEDICATED_SERVER;
        //? } else {
        /*return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        *///? }
    }
}
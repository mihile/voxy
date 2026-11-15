package me.cortex.voxy.common.platform;

import java.nio.file.Path;
import java.util.List;

public interface PlatformUtil {
    boolean isModLoaded(String modId);

    /** Returns the root path of the given mod, or null if unavailable. */
    Path getModRootPath(String modId);

    /** Returns the mod version string, or null if unavailable. */
    String getModVersion(String modId);

    /** Returns the Flashback replay storage path, or null if unavailable. */
    Path getReplayStoragePath(boolean flashbackInstalled);

    /** Returns the config directory path for the current runtime, used for config file placement. */
    Path getConfigDir();

    /** True when running as a dedicated server. */
    boolean isDedicatedServer();
}
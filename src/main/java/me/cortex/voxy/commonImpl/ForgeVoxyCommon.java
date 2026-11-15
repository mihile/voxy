package me.cortex.voxy.commonImpl;

import net.minecraftforge.fml.common.Mod;


@Mod("voxy")
public class ForgeVoxyCommon extends VoxyCommon {
    public ForgeVoxyCommon() {
        com.mojang.logging.LogUtils.getLogger().info("Initializing Voxy Common");
    }
}

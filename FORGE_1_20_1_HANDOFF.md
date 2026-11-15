# Voxy 1.20.1 Forge Hybrid (`1.20.1_forge`)

- **Branch**: `1.20.1_forge` (branched from commit `802be8af`)
- **Remote (`fork`)**: `https://github.com/mihile/voxy.git`
- **Artifact / Version**: `modId = voxy`, `mod_version = 0.4.0beta+hybrid.1`, Minecraft `1.20.1`, Forge `47.3.0+`
- **Companion Server Mod**: `VoxyServer` (`https://github.com/mihile/VoxyServer.git`), branch `1.20.1_forge` (`forge-1.20.1/` subdirectory)

## What This Branch Contains
- Full Minecraft 1.20.1 Forge port converted from the 1.20.1 Fabric backport (`ForgeVoxyClient`, `ForgeVoxyCommon`, `META-INF/mods.toml`, Loom Forge build).
- Integrated Neo-Voxy 0.4.0beta compatibility and features (Surface world view/migration, Create distant contraption/track/kinetic rendering, distant beacon renderer, `BiomeBlendPalette`, Embeddium/Oculus mixins).
- Deterministic ordering of `uniforms`, `samplerSet`, and `ssbos` in `IrisVoxyRenderPipelineData.java` so Iris shader binary disk cache entries remain stable and hit across client restarts.

## Build & Test
```powershell
.\gradlew.bat test build --no-daemon
```
- Output JAR: `build/libs/voxy-0.4.0beta+hybrid.1-mc1.20.1-forge-client.jar`
- Note: Pass this JAR via `-Pvoxy_jar=<path>` when building `VoxyServer` under `forge-1.20.1/`.

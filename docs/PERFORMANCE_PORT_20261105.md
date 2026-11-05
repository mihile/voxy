# 1.20.1 port.10: native memory and stored-mapping work

Ported from the reviewed Prominence compatibility layer:
- Load historical block mappings without eagerly calculating unused opacity. New states remain eager; leaf opacity stays 15. Resolve each stored value once under a monitor, publish it through volatile fields, and retry normally after failures. Internal readers use `resolvedOpacity()`.
- Dedicated servers bound secondary section caching to 512 by default. `-Dvoxy.secondaryCacheSections=N` controls it; nonpositive values preserve the original capacity. The legacy Prominence property is also accepted. Clients retain the original capacity.

port.9 already contains shader binary caching, renderer/resource reuse and storage failure cleanup. EMI-specific frame coordination, atlas invalidation policy and client RocksDB retry policy remain in the compatibility JAR; they are not generic generation changes. Retired mesh-admission/state-remap experiments were not restored.

Verified with Java 17 remapJar + test, including real stored block-state serialization, concurrent opacity lookup and existing storage-failure tests. No game, production server or world launched. Actual FPS/heap improvement is unmeasured. Compatibility JAR fingerprints are intentionally not widened: old target bytes retain their old fixes; changed native targets disable those old injections.

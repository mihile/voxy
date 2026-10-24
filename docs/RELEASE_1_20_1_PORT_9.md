# Minecraft 1.20.1 port.9

This release publishes the previously local 1.20.1 renderer and storage fixes.
It retains the login initialization and animated-texture fixes from port.8.

- Defer renderer creation until a world frame has selected its Iris pipeline;
  coalesce redundant reloads and remove constructor-triggered explicit GC.
- Stabilize Iris shader variable layout, handle the affected volumetric-light
  coordinate expression, and retain the ordinary shader fallback.
- Cache bounded shader program binaries per GL context. Reuse one bounded CPU
  model-atlas array across renderer replacement, then clear retained resources
  on session shutdown.
- Use a fenced atlas PBO transfer, persistent pre-mapping where supported, and
  worker-side CPU copying. Preserve render-thread ownership of GL operations,
  timeout cleanup, and synchronous fallback for unsupported paths.
- Release world-creation locks and native RocksDB resources on failed creation
  and cleanup. Do not delete database LOCK files or move existing caches.

Validation: Java 17 target build; nine passing unit tests (two login bytecode
regressions and seven storage/creation failure tests) on GraalVM 25.4. Release
JARs exclude verification/JUnit classes. Before this publication, the renderer
classes were already used in local modpack profiling; the rebuilt pre-commit
JAR matched all installed class and resource entries except build metadata.
This publication does not constitute a new graphical/GL-driver compatibility
test or a controlled performance benchmark. No game client, server or world
was launched for this publication.

The optional `verification/storage` source set builds a separate verification
mod. It is not included in the production artifact.

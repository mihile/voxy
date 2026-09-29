# Voxy 1.20.1 - Faster Iris Shadow Mapper compatibility fork

This fork ports Voxy 0.2.20-beta to Minecraft 1.20.1 for use with
Faster Iris Shadow Mapper. It fixes the severe animated-texture stutter
triggered when Voxy is used together with Faster Iris Shadow Mapper 1.0.6 and
Iris 1.7.6. It is based on the upstream Voxy project by MCRcortex and the
existing 1.20.1 backport work.

## Upstream improvements included

- Brings over the latest upstream section data preparation optimization.
- Supports the shader pack `useDynamicFarPlane` option, which sizes Voxy's far
  projection plane to the configured render distance when requested.
- Carries the upstream viewport argument through depth and stencil setup.

Minecraft 26.2 dependency updates from upstream are not applicable to this
Minecraft 1.20.1 port.

## Changes in this fork

- Allows Faster Iris Shadow Mapper to remain enabled by avoiding the severe
  animation-upload stalls exposed by its Iris shadow-pass workload.
- Fixes those stalls with asynchronous OpenGL pixel buffer object (PBO)
  uploads.
- Keeps the original texture animations and their interpolation fully enabled.
- Prevents the same Sodium chunk from being queued for Voxy ingestion twice.

Minecraft normally uploads animated atlas frames directly from CPU memory.
When the GPU is still reading that atlas, `glTexSubImage2D` can block the render
thread. This fork copies each updated frame into an orphaned pixel-unpack buffer
and queues the texture transfer asynchronously. Pixel contents, animation
timing, interpolation, resource packs, and visual effects are not disabled or
altered.

The asynchronous upload can be disabled for troubleshooting with:

```text
-Dvoxy.asyncTextureAnimationUploads=false
```

## Faster Iris Shadow Mapper compatibility fix

The stutter was originally observed with Voxy 0.2.19-beta, Faster Iris Shadow
Mapper 1.0.6, and Iris 1.7.6 on Minecraft 1.20.1. Faster Iris Shadow Mapper
triggers the stutter; disabling it avoids the problem but also removes its
shadow-pass optimization. This patch instead changes animated texture transfers to use an
asynchronous PBO upload path, allowing Faster Iris Shadow Mapper and Voxy to be
used together without disabling texture animations or visual effects.

Faster Iris Shadow Mapper does not directly replace Voxy's texture upload code.
It skips selected draw work during the Iris shadow pass, such as sign text, map
contents, banner patterns, and enchantment glint. The available profiling
evidence therefore points to a GPU/driver synchronization issue exposed by the
altered shadow-pass workload and timing, rather than a direct conflicting mixin.

## Build

```text
./gradlew build
```

The remapped Fabric JAR is written to `build/libs`.

## Permission and upstream license

You are welcome to use, copy, modify, and adapt the original changes introduced
by this fork for any purpose.

The upstream Voxy source code remains copyright MCRcortex and is still governed
by the original [LICENSE.md](LICENSE.md). This permission applies only to the
new changes authored for this fork; it does not relicense the upstream project
or grant permission to redistribute upstream source code or compiled builds.

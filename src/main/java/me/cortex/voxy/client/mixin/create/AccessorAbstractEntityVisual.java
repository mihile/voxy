package me.cortex.voxy.client.mixin.create;

import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractEntityVisual.class, remap = false)
public interface AccessorAbstractEntityVisual {
    @Accessor("entity")
    Entity voxy$getEntity();
}

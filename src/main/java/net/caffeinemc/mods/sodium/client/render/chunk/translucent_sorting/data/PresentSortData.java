package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.util.CachedNativeBuffer;

import java.nio.IntBuffer;

public interface PresentSortData {
    CachedNativeBuffer getIndexBuffer();

    default IntBuffer getIntBuffer() {
        return this.getIndexBuffer().getDirectBuffer().asIntBuffer();
    }
}

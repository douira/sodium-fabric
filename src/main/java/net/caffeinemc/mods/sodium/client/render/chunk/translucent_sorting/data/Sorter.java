package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.util.BufferCache;
import net.caffeinemc.mods.sodium.client.util.CachedNativeBuffer;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public abstract class Sorter implements PresentSortData {
    private CachedNativeBuffer indexBuffer;

    public abstract void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial);

    @Override
    public CachedNativeBuffer getIndexBuffer() {
        return this.indexBuffer;
    }

    void initBufferWithQuadLength(int quadCount) {
        this.indexBuffer = BufferCache.instance().acquire(TranslucentData.quadCountToIndexBytes(quadCount));
    }
}

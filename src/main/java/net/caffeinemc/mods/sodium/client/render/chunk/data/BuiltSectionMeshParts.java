package net.caffeinemc.mods.sodium.client.render.chunk.data;

import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.util.CachedNativeBuffer;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public class BuiltSectionMeshParts {
    private final VertexRange[] ranges;
    private final CachedNativeBuffer buffer;

    public BuiltSectionMeshParts(CachedNativeBuffer buffer, VertexRange[] ranges) {
        this.ranges = ranges;
        this.buffer = buffer;
    }

    public CachedNativeBuffer getVertexData() {
        return this.buffer;
    }

    public VertexRange[] getVertexRanges() {
        return this.ranges;
    }
}

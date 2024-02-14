package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.core.SectionPos;

/**
 * Super class for translucent data that contains an actual buffer.
 */
public abstract class PresentTranslucentData extends TranslucentData {
    private NativeBuffer buffer;
    private boolean reuseUploadedData;
    private int quadHash;
    private final int length;
    private final int byteLength;

    PresentTranslucentData(SectionPos sectionPos, NativeBuffer buffer) {
        super(sectionPos);
        this.buffer = buffer;
        this.byteLength = buffer.getLength();
        this.length = TranslucentData.indexBytesToQuadCount(this.byteLength);
    }

    public abstract VertexRange[] getVertexRanges();

    @Override
    public void destroy() {
        super.destroy();
        this.deleteBuffer();
    }

    void deleteBuffer() {
        if (this.buffer != null) {
            this.buffer.free();
            this.buffer = null;
        }
    }

    public void createSizedBuffer() {
        this.buffer = nativeBufferForQuads(this.length);
    }

    public void setQuadHash(int hash) {
        this.quadHash = hash;
    }

    public int getQuadHash() {
        return this.quadHash;
    }

    public int getLength() {
        return this.length;
    }

    public int getByteLength() {
        return this.byteLength;
    }

    public NativeBuffer getBuffer() {
        return this.buffer;
    }

    public boolean isReusingUploadedData() {
        return this.reuseUploadedData;
    }

    public void setReuseUploadedData() {
        this.reuseUploadedData = true;
    }

    public void unsetReuseUploadedData() {
        this.reuseUploadedData = false;
    }

    public static NativeBuffer nativeBufferForQuads(int quadCount) {
        return new NativeBuffer(TranslucentData.quadCountToIndexBytes(quadCount));
    }

    public static NativeBuffer nativeBufferForQuads(TQuad[] quads) {
        return nativeBufferForQuads(quads.length);
    }

    public static NativeBuffer nativeBufferForQuads(NativeBuffer existing, TQuad[] quads) {
        if (existing != null) {
            return existing;
        }
        return nativeBufferForQuads(quads);
    }
}

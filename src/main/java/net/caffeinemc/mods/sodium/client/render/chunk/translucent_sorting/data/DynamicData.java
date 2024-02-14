package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.resource.CachedResource;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.resource.ResourceInterface;
import org.joml.Vector3dc;

import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.core.SectionPos;

public abstract class DynamicData extends MixedDirectionData implements ResourceInterface {
    private GeometryPlanes geometryPlanes;
    private final Vector3dc initialCameraPos;
    private final CachedResource bufferResource = new CachedResource(this);

    DynamicData(SectionPos sectionPos, NativeBuffer buffer, VertexRange range, GeometryPlanes geometryPlanes, Vector3dc initialCameraPos) {
        super(sectionPos, buffer, range);
        this.geometryPlanes = geometryPlanes;
        this.initialCameraPos = initialCameraPos;

        this.bufferResource.initialReleaseWithoutSignal();
    }

    @Override
    public void destroy() {
        super.destroy();
        this.bufferResource.destroy();
    }

    @Override
    public SortType getSortType() {
        return SortType.DYNAMIC;
    }

    @Override
    public void notifyAfterUpload() {
        this.bufferResource.release();
    }

    public GeometryPlanes getGeometryPlanes() {
        return this.geometryPlanes;
    }

    public void clearGeometryPlanes() {
        this.geometryPlanes = null;
    }

    public Vector3dc getInitialCameraPos() {
        return this.initialCameraPos;
    }

    @Override
    public void createResource() {
        this.createSizedBuffer();
    }

    @Override
    public boolean isResourcePresent() {
        return this.getBuffer() != null;
    }

    @Override
    public void deleteResource() {
        this.deleteBuffer();
    }

    @Override
    public int getSize() {
        return this.getByteLength();
    }

    protected NativeBuffer ensureAndGetBuffer() {
        this.bufferResource.acquire();
        return this.getBuffer();
    }
}

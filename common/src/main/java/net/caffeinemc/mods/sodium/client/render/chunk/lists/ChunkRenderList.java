package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.util.iterator.ReversibleByteArrayIterator;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteArrayIterator;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

public class ChunkRenderList {
    private final RenderRegion region;

    private final long[] sectionsWithGeometry = RegionSectionTree.makeTree();
    private int sectionsWithGeometryCount = 0;

    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    private int size;

    private int lastVisibleFrame;

    public ChunkRenderList(RenderRegion region) {
        this.region = region;
    }

    public void reset(int frame) {
        this.sectionsWithGeometryCount = 0;
        this.sectionsWithSpritesCount = 0;
        this.sectionsWithEntitiesCount = 0;

        this.size = 0;
        this.lastVisibleFrame = frame;
    }

    public void add(RenderSection render) {
        if (this.size >= RenderRegion.REGION_SIZE) {
            throw new ArrayIndexOutOfBoundsException("Render list is full");
        }

        this.size++;

        int index = render.getSectionIndex();
        int flags = render.getFlags();

        if (((flags >>> RenderSectionFlags.HAS_BLOCK_GEOMETRY) & 1) != 0) {
            RegionSectionTree.add(this.sectionsWithGeometry, index);
            this.sectionsWithGeometryCount++;
        }

        if (((flags >>> RenderSectionFlags.HAS_ANIMATED_SPRITES) & 1) != 0) {
            this.sectionsWithSprites[this.sectionsWithSpritesCount] = (byte) index;
            this.sectionsWithSpritesCount++;
        }

        if (((flags >>> RenderSectionFlags.HAS_BLOCK_ENTITIES) & 1) != 0) {
            this.sectionsWithEntities[this.sectionsWithEntitiesCount] = (byte) index;
            this.sectionsWithEntitiesCount++;
        }
    }

    public long[] getSectionsWithGeometry() {
        return this.sectionsWithGeometry;
    }

    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public int size() {
        return this.size;
    }
}

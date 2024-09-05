package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.LinearSectionOctree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;

/**
 * The visible chunk collector is passed to the occlusion graph search culler to
 * collect the visible chunks.
 */
public class VisibleChunkCollectorAsync implements LinearSectionOctree.VisibleSectionVisitor  {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;

    private final RenderRegionManager regions;

    private final int frame;

    public VisibleChunkCollectorAsync(RenderRegionManager regions, int frame) {
        this.regions = regions;
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
    }

    @Override
    public void visit(int x, int y, int z) {
        var region = this.regions.getForChunk(x, y, z);

        // since this is async, the region might have been removed in the meantime
        if (region == null) {
            return;
        }

        int rX = x & (RenderRegion.REGION_WIDTH - 1);
        int rY = y & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = z & (RenderRegion.REGION_LENGTH - 1);
        var sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);

        ChunkRenderList renderList = region.getRenderList();

        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        // flags don't need to be checked here since only sections with contents (RenderSectionFlags.MASK_NEEDS_RENDER) are added to the octree
        renderList.add(sectionIndex);
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }
}

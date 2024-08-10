package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.minecraft.core.SectionPos;

/**
 * The BSP workspace holds the state during the BSP building process. (see also
 * BSPSortState) It brings a number of fixed parameters and receives partition
 * planes to return as part of the final result.
 * 
 * Implementation note: Storing the multi partition node's interval points in a
 * global array instead of making a new one at each tree level doesn't appear to
 * have any performance benefit.
 */
class BSPWorkspace {
    /**
     * All the quads in the section.
     */
    TQuad[] quads;
    int quadCount;
    private final SectionPos sectionPos;
    final boolean prepareNodeReuse;
    private final ChunkBuildBuffers buffers;
    private final BSPResult result = new BSPResult();

    BSPWorkspace(TQuad[] quads, SectionPos sectionPos, boolean prepareNodeReuse, ChunkBuildBuffers buffers) {
        this.quads = quads;
        this.quadCount = quads.length;
        this.sectionPos = sectionPos;
        this.prepareNodeReuse = prepareNodeReuse;
        this.buffers = buffers;
    }

    BSPResult getResult() {
        return this.result;
    }

    // TODO: better bidirectional triggering: integrate bidirectionality in GFNI if
    // top-level topo sorting isn't used anymore (and only use half as much memory
    // by not storing it double)
    void addAlignedPartitionPlane(int axis, float distance) {
        this.result.addDoubleSidedPlane(this.sectionPos, axis, distance);
    }

    int addQuad(TQuad quad) {
        if (this.quadCount == this.quads.length) {
            var newQuads = new TQuad[this.quads.length * 2];
            System.arraycopy(this.quads, 0, newQuads, 0, this.quads.length);
            this.quads = newQuads;
        }
        int index = this.quadCount++;
        this.quads[index] = quad;
        return index;
    }
}

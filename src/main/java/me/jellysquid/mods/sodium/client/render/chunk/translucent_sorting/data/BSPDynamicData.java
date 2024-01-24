package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.data;

import org.joml.Vector3dc;
import org.joml.Vector3fc;

import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPNode;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPResult;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Constructs a BSP tree of the quads and sorts them dynamically.
 * 
 * Triggering is performed when the BSP tree's partition planes are crossed in
 * any direction (bidirectional).
 */
public class BSPDynamicData extends DynamicData {
    private static final int NODE_REUSE_MIN_GENERATION = 1;

    private final BSPNode rootNode;
    private final int generation;

    private BSPDynamicData(ChunkSectionPos sectionPos,
            NativeBuffer buffer, VertexRange range, BSPResult result, Vector3dc cameraPos, int generation) {
        super(sectionPos, buffer, range, result, cameraPos);
        this.rootNode = result.getRootNode();
        this.generation = generation;
    }

    @Override
    public void sortOnTrigger(Vector3fc cameraPos) {
        this.sort(cameraPos);
    }

    private void sort(Vector3fc cameraPos) {
        this.unsetReuseUploadedData();

        this.rootNode.collectSortedQuads(getBuffer(), cameraPos);
    }

    public static BSPDynamicData fromMesh(BuiltSectionMeshParts translucentMesh,
            CombinedCameraPos cameraPos, TQuad[] quads, ChunkSectionPos sectionPos,
            NativeBuffer buffer, TranslucentData oldData) {
        BSPNode oldRoot = null;
        int generation = 0;
        boolean prepareNodeReuse = false;
        if (oldData instanceof BSPDynamicData oldBSPData) {
            generation = oldBSPData.generation + 1;
            oldRoot = oldBSPData.rootNode;

            // only enable partial updates after a certain number of generations
            // (times the section has been built)
            prepareNodeReuse = generation >= NODE_REUSE_MIN_GENERATION;
        }
        var result = BSPNode.buildBSP(quads, sectionPos, oldRoot, prepareNodeReuse);

        VertexRange range = TranslucentData.getUnassignedVertexRange(translucentMesh);
        buffer = PresentTranslucentData.nativeBufferForQuads(buffer, quads);

        var dynamicData = new BSPDynamicData(sectionPos, buffer, range, result,
                cameraPos.getAbsoluteCameraPos(), generation);
        dynamicData.sort(cameraPos.getRelativeCameraPos());

        // prepare geometry planes for integration into GFNI triggering
        result.prepareIntegration();

        return dynamicData;
    }

    /**
     * Calculates the offset to apply to each old quad to map it to where it is in
     * the new quad array. However these values are stored in the positions of the
     * new quads that they are mapped to, so that in the partitioning algorithm they
     * can be evaluated. Only additions, removals and changes of quads are tracked.
     * Changes in order are not fully taken advantage of since they're modelled as a
     * removal and an addition. (only half of such a reordering is tracked)
     */
    private static int[] computeQuadMatchOffsets(float[][] oldExtents, float[][] newExtents) {
        final int LOOKAHEAD = 10;
        var quadMatchOffsets = new int[newExtents.length];

        Arrays.fill(quadMatchOffsets, Integer.MAX_VALUE);

        // two indexes iterate the two lists of quads and look for matches within a
        // certain limit
        int oldBaseIndex = 0;
        int newBaseIndex = 0;
        while (oldBaseIndex < oldExtents.length && newBaseIndex < newExtents.length) {
            // if there's no offset for this old quad yet
            if (quadMatchOffsets[oldBaseIndex] == Integer.MAX_VALUE) {
                // look for a match within a certain limit
                var oldSearchIndex = oldBaseIndex;
                var newSearchIndex = newBaseIndex;
                for (int i = 0; i < LOOKAHEAD && oldSearchIndex < oldExtents.length
                        && newSearchIndex < newExtents.length; i++, oldSearchIndex++, newSearchIndex++) {
                    // check for cross matches: match current search position of the one with the
                    // base positon of the other in both directions.

                    if (TQuad.extentsEqual(oldExtents[newBaseIndex], newExtents[oldSearchIndex])) {
                        // found a match of the base new quad with the old search quad.
                        // Advance the base indexes to be at the two matches and write the offset with
                        // which to map the old quad to its match in the new quad array to the position
                        // of the new quad.
                        oldBaseIndex = oldSearchIndex;
                        quadMatchOffsets[newBaseIndex] = newBaseIndex - oldBaseIndex;
                        // new base index stays the same
                        break;
                    }

                    // if this isn't the case where the search index hasnt' advanced yet, check for
                    // the other match direction too
                    if (i > 0 && TQuad.extentsEqual(oldExtents[oldBaseIndex], newExtents[newSearchIndex])) {
                        newBaseIndex = newSearchIndex;
                        quadMatchOffsets[newBaseIndex] = newBaseIndex - oldBaseIndex;
                        break;
                    }
                }
            }

            // increment both to look for the next match
            oldBaseIndex++;
            newBaseIndex++;
        }

        return quadMatchOffsets;
    }
}

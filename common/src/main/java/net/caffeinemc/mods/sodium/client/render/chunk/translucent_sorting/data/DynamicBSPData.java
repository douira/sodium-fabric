package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.render.measurement.Counter;
import net.caffeinemc.mods.sodium.client.render.measurement.HeuristicType;
import net.caffeinemc.mods.sodium.client.render.measurement.Measurement;
import net.caffeinemc.mods.sodium.client.render.measurement.TimingRecorder;
import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPNode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPResult;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

/**
 * Constructs a BSP tree of the quads and sorts them dynamically.
 *
 * Triggering is performed when the BSP tree's partition planes are crossed in
 * any direction (bidirectional).
 */
public class DynamicBSPData extends DynamicData {
    // /tp ~ ~-100 ~
    private static final TimingRecorder sortInitialRecorder = new TimingRecorder("BSP sort initial");
    private static final TimingRecorder sortTriggerRecorder = new TimingRecorder("BSP sort trigger");
    private static final TimingRecorder directSortComparisonRecorder = new TimingRecorder("BSP direct sort comparison");
    private static final TimingRecorder buildRecorder = new TimingRecorder("BSP build");
    private static final TimingRecorder partialUpdateRecorder = new TimingRecorder("BSP partial update", 10, false);
    private static final int NODE_REUSE_MIN_GENERATION = 1;

    private final BSPNode rootNode;
    private final int generation;

    private DynamicBSPData(SectionPos sectionPos, int vertexCount, BSPResult result, Vector3dc initialCameraPos, TQuad[] quads, int generation) {
        super(sectionPos, vertexCount, quads.length, result, initialCameraPos);
        this.rootNode = result.getRootNode();
        this.generation = generation;
        this.heuristicType = HeuristicType.BSP;
    }

    private class DynamicBSPSorter extends DynamicSorter {
        private DynamicBSPSorter(int quadCount) {
            super(quadCount);
        }

        @Override
        void writeSort(CombinedCameraPos cameraPos, boolean initial) {
            var start = System.nanoTime();
            DynamicBSPData.this.rootNode.collectSortedQuads(this.getIndexBuffer(), cameraPos.getRelativeCameraPos());
            sortTriggerRecorder.recordNow(this.getLength(), start);
        }
    }

    @Override
    public Sorter getSorter() {
        return new DynamicBSPSorter(this.getQuadCount());
    }

    public static DynamicBSPData fromMesh(int vertexCount,
                                          CombinedCameraPos cameraPos, TQuad[] quads, SectionPos sectionPos,
                                          TranslucentData oldData) {
        BSPNode oldRoot = null;
        int generation = 0;
        boolean prepareNodeReuse = false;
        if (oldData instanceof DynamicBSPData oldBSPData) {
            generation = oldBSPData.generation + 1;
            oldRoot = oldBSPData.rootNode;

            // only enable partial updates after a certain number of generations
            // (times the section has been built)
            prepareNodeReuse = generation >= NODE_REUSE_MIN_GENERATION;
        }

        var start = System.nanoTime();
        var result = BSPNode.buildBSP(quads, sectionPos, oldRoot, prepareNodeReuse);
        if (oldRoot == null) {
            buildRecorder.recordNow(quads.length, start);
        } else {
            partialUpdateRecorder.recordNow(quads.length, start);
        }
        Counter.UNIQUE_TRIGGERS.incrementBy(result.countUniqueTriggers());

        var dynamicData = new DynamicBSPData(sectionPos, vertexCount, result, cameraPos.getAbsoluteCameraPos(), quads, generation);

        // NOTE: removed direct sort comparison during merge
        if (Measurement.DEBUG_TRIGGER_STATS) {
            Counter.UNIQUE_TRIGGERS.incrementBy(result.getUniqueTriggers());
        }
        Counter.QUADS.incrementBy(quads.length);
        Counter.BSP_SECTIONS.increment();

        // prepare geometry planes for integration into GFNI triggering
        result.prepareIntegration();

        return dynamicData;
    }
}

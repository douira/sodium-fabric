package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.data;

import org.joml.Vector3dc;
import org.joml.Vector3fc;

import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPNode;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPResult;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.TimingRecorder;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.TimingRecorder.Counter;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Constructs a BSP tree of the quads and sorts them dynamically.
 * 
 * Triggering is performed when the BSP tree's partition planes are crossed in
 * any direction (bidirectional).
 */
public class BSPDynamicData extends DynamicData {
    // /tp ~ ~-100 ~
    public static final TimingRecorder sortInitialRecorder = new TimingRecorder("BSP sort initial");
    public static final TimingRecorder sortTriggerRecorder = new TimingRecorder("BSP sort trigger");
    public static final TimingRecorder buildRecorder = new TimingRecorder("BSP build");
    public static final TimingRecorder partialUpdateRecorder = new TimingRecorder("BSP partial update", 10, true);

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
        var start = System.nanoTime();
        this.sort(cameraPos);
        sortTriggerRecorder.recordNow(this.getLength(), start);
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

        var start = System.nanoTime();
        var result = BSPNode.buildBSP(quads, sectionPos, oldRoot, prepareNodeReuse);
        if (oldRoot == null) {
            buildRecorder.recordNow(quads.length, start);
        } else {
            partialUpdateRecorder.recordNow(quads.length, start);
        }
        Counter.UNIQUE_TRIGGERS.incrementBy(result.countUniqueTriggers());

        VertexRange range = TranslucentData.getUnassignedVertexRange(translucentMesh);
        buffer = PresentTranslucentData.nativeBufferForQuads(buffer, quads);

        var dynamicData = new BSPDynamicData(sectionPos, buffer, range, result,
                cameraPos.getAbsoluteCameraPos(), generation);

        start = System.nanoTime();
        dynamicData.sort(cameraPos.getRelativeCameraPos());
        sortInitialRecorder.recordNow(quads.length, start);

        if (SortTriggering.DEBUG_TRIGGER_STATS) {
            Counter.UNIQUE_TRIGGERS.incrementBy(result.getUniqueTriggers());
        }
        Counter.QUADS.incrementBy(quads.length);
        Counter.BSP_SECTIONS.increment();

        // prepare geometry planes for integration into GFNI triggering
        result.prepareIntegration();

        return dynamicData;
    }
}

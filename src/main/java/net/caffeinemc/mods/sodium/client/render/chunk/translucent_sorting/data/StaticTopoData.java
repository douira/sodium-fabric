package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.util.BufferCache;
import net.caffeinemc.mods.sodium.client.render.measurement.TimingRecorder;
import net.minecraft.core.SectionPos;

import java.nio.IntBuffer;
import java.util.function.IntConsumer;

/**
 * Static topo acyclic sorting uses the topo sorting algorithm but only if it's
 * possible to sort without dynamic triggering, meaning the sort order never
 * needs to change.
 */
public class StaticTopoData extends MixedDirectionData {
    private static final TimingRecorder topoSortRecorder = new TimingRecorder("Topo Sort");

    private Sorter sorterOnce;

    StaticTopoData(SectionPos sectionPos, VertexRange range, int quadCount) {
        super(sectionPos, range, quadCount);
    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_TOPO;
    }

    @Override
    public Sorter getSorter() {
        var sorter = this.sorterOnce;
        if (sorter == null) {
            throw new IllegalStateException("Sorter already used!");
        }
        this.sorterOnce = null;
        return sorter;
    }

    private record QuadIndexConsumerIntoBuffer(IntBuffer buffer) implements IntConsumer {
        @Override
        public void accept(int value) {
            TranslucentData.writeQuadVertexIndexes(this.buffer, value);
        }
    }

    public static StaticTopoData fromMesh(BuiltSectionMeshParts translucentMesh, TQuad[] quads, SectionPos sectionPos) {
        VertexRange range = TranslucentData.getUnassignedVertexRange(translucentMesh);
        var sorter = new StaticSorter(quads.length);
        var indexWriter = new QuadIndexConsumerIntoBuffer(sorter.getIntBuffer());

        var start = System.nanoTime();
        if (!TopoGraphSorting.topoGraphSort(indexWriter, quads, null, null)) {
            BufferCache.instance().release(sorter.getIndexBuffer());
            return null;
        }
        topoSortRecorder.recordNow(quads.length, start);

        var staticTopoData = new StaticTopoData(sectionPos, range, quads.length);
        staticTopoData.sorterOnce = sorter;
        return staticTopoData;
    }
}

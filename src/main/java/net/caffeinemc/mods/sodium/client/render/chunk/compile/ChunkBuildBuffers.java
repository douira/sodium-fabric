package net.caffeinemc.mods.sodium.client.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.util.VertexRange;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

import java.util.Arrays;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    private final ChunkVertexType vertexType;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        this.vertexType = vertexType;

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertexBuffers[facing] = new ChunkMeshBufferBuilder(this.vertexType, 128 * 1024);
            }

            this.builders.put(pass, new BakedChunkModelBuilder(vertexBuffers));
        }
    }

    public void init(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    public ChunkModelBuilder get(Material material) {
        return this.builders.get(material.pass);
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, int visibleSlices, boolean reverse, boolean forceUnassigned, boolean sliceReordering) {
        var builder = this.builders.get(pass);

        VertexRange[] vertexRanges = new VertexRange[ModelQuadFacing.COUNT];
        int vertexCount = 0;

        // get the total vertex count to initialize the buffer
        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            vertexCount += builder.getVertexBuffer(facing).count();
        }

        if (vertexCount == 0) {
            return null;
        }

        var mergedBuffer = new NativeBuffer(vertexCount * this.vertexType.getVertexFormat().getStride());
        var mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        if (sliceReordering) {
            // sliceReordering implies !forceUnassigned

            // write all currently visible slices first, and then the rest.
            // start with unassigned as it will never become invisible
            var unassignedBuffer = builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED);
            int vertexRangeCount = 0;
            vertexRanges[vertexRangeCount++] = new VertexRange(unassignedBuffer.count(), ModelQuadFacing.UNASSIGNED.ordinal());

            // write all visible and then invisible slices
            for (var step = 0; step < 2; step++) {
                for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                    var facingIndex = facing.ordinal();
                    if (facing == ModelQuadFacing.UNASSIGNED || ((visibleSlices >> facingIndex) & 1) == step) {
                        continue;
                    }

                    // generate empty ranges to prevent SectionRenderData storage from making up indexes for null ranges
                    var buffer = builder.getVertexBuffer(facing);
                    vertexRanges[vertexRangeCount++] = new VertexRange(buffer.count(), facingIndex);
                }
            }

            // reverse the vertex ranges for half the sections
            if (reverse) {
                var half = vertexRangeCount / 2;
                for (var i = 0; i < half; i++) {
                    var temp = vertexRanges[i];
                    vertexRanges[i] = vertexRanges[vertexRangeCount - i - 1];
                    vertexRanges[vertexRangeCount - i - 1] = temp;
                }
            }

            // write the buffers based on the ranges
            for (var range : vertexRanges) {
                var buffer = builder.getVertexBuffer(ModelQuadFacing.VALUES[range.facing()]);
                if (!buffer.isEmpty()) {
                    mergedBufferBuilder.put(buffer.slice());
                }
            }
        } else {
            // forceUnassigned implies !sliceReordering

            if (forceUnassigned) {
                vertexRanges[ModelQuadFacing.UNASSIGNED.ordinal()] = new VertexRange(vertexCount, ModelQuadFacing.UNASSIGNED.ordinal());
            }

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                var buffer = builder.getVertexBuffer(facing);
                if (!buffer.isEmpty()) {
                    if (!forceUnassigned) {
                        var facingIndex = facing.ordinal();
                        vertexRanges[facingIndex] = new VertexRange(buffer.count(), facingIndex);
                    }
                    mergedBufferBuilder.put(buffer.slice());
                }
            }
        }

        return new BuiltSectionMeshParts(mergedBuffer, vertexRanges);
    }

    public void destroy() {
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }
}

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
     *
     * @param pass            The render pass to create the mesh for
     * @param forceUnassigned If true, all geometry will be rendered with the unassigned facing
     * @param sliceReordering If true, the slices will be reordered to minimize the number of draw commands
     * @param xDelta          The x component of the vector from the camera to the section
     * @param yDelta          The y component of the vector from the camera to the section
     * @param zDelta          The z component of the vector from the camera to the section
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, boolean forceUnassigned, boolean sliceReordering, int xDelta, int yDelta, int zDelta) {
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

        if (forceUnassigned) {
            vertexRanges[ModelQuadFacing.UNASSIGNED.ordinal()] = new VertexRange(0, vertexCount);
        }

        var mergedBuffer = new NativeBuffer(vertexCount * this.vertexType.getVertexFormat().getStride());
        var mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        if (sliceReordering) {
            // sliceReordering implies !forceUnassigned

            var orderedFacings = Arrays.copyOf(ModelQuadFacing.VALUES, ModelQuadFacing.COUNT);
            var facingSortKeys = new int[] {
                    xDelta, yDelta, zDelta, -xDelta, -yDelta, -zDelta, 0
            };

            Arrays.sort(orderedFacings, (a, b) -> {
                var aKey = facingSortKeys[a.ordinal()];
                var bKey = facingSortKeys[b.ordinal()];
                var result = Integer.compare(aKey, bKey);

                // compare two negative keys backwards to avoid mirroring around 0
                if (aKey < 0 && bKey < 0) {
                    result = -result;
                }

                return result;
            });


            for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
                var facing = orderedFacings[i];
                var buffer = builder.getVertexBuffer(facing);

                vertexRanges[i] = new VertexRange(buffer.count(), facing.ordinal());

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

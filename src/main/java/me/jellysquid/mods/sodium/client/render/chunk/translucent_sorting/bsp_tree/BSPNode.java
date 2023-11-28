package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import java.nio.IntBuffer;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.TopoGraphSorting;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * A node in the BSP tree. The BSP tree is made up of nodes that split quads
 * into groups on either side of a plane and those that lie on the plane.
 * There's also leaf nodes that contain one or more quads.
 * 
 * TODO:
 * - historgram of BSP build and sort times on a standard world loading scenario
 * (maybe something like hermitcraft?) with some artificial structures too.
 */
public abstract class BSPNode {
    public abstract void collectSortedQuads(IntBuffer indexBuffer, Vector3fc cameraPos);

    public static BSPResult buildBSP(TQuad[] quads, ChunkSectionPos sectionPos) {
        // throw if there's too many quads
        InnerPartitionBSPNode.validateQuadCount(quads.length);

        // create a workspace and then the nodes figure out the recursive building.
        // throws if the BSP can't be built, null if none is necessary
        var workspace = new BSPWorkspace(quads, sectionPos);
        var rootNode = BSPNode.build(workspace);
        var result = workspace.result;
        result.rootNode = rootNode;
        return result;
    }

    static BSPNode build(BSPWorkspace workspace) {
        // pick which type of node to create for the given workspace
        // TODO: add more heuristics here
        if (workspace.indexes.isEmpty()) {
            return null;
        } else if (workspace.indexes.size() == 1) {
            return new LeafSingleBSPNode(workspace.indexes.getInt(0));
        }

        // special case two quads
        var indexes = workspace.indexes;
        if (indexes.size() == 2) {
            var quadIndexA = indexes.getInt(0);
            var quadIndexB = indexes.getInt(1);
            var quadA = workspace.quads[quadIndexA];
            var quadB = workspace.quads[quadIndexB];

            // check for coplanar or mutually invisible quads
            var facingA = quadA.facing();
            var facingB = quadB.facing();
            var normalA = quadA.normal();
            var normalB = quadB.normal();
            if (
            // coplanar quads
            ((facingA == ModelQuadFacing.UNASSIGNED || facingB == ModelQuadFacing.UNASSIGNED)
                    ? // opposite normal (distance irrelevant)
                    normalA.x() == -normalB.x()
                            && normalA.y() == -normalB.y()
                            && normalA.z() == -normalB.z()
                            // same normal and same distance
                            || normalA.equals(quadB.normal())
                                    && normalA.dot(quadA.center()) == quadB.normal().dot(quadB.center())
                    // aligned same distance
                    : quadA.extents()[facingA.ordinal()] == quadB.extents()[facingB.ordinal()])
                    // facing away from eachother
                    || facingA == facingB.getOpposite()
                    // otherwise mutually invisible
                    || facingA != ModelQuadFacing.UNASSIGNED
                            && facingB != ModelQuadFacing.UNASSIGNED
                            && !TopoGraphSorting.orthogonalQuadVisibleThrough(quadA, quadB)
                            && !TopoGraphSorting.orthogonalQuadVisibleThrough(quadB, quadA)) {
                return new LeafDoubleBSPNode(quadIndexA, quadIndexB);
            }
        }

        return InnerPartitionBSPNode.build(workspace);
    }

    static BSPNode buildChild(BSPWorkspace workspace, IntArrayList indexes, int currentAxisOffset) {
        workspace.indexes = indexes;
        workspace.axisOffset = (currentAxisOffset + 1) % 3;
        return build(workspace);
    }
}
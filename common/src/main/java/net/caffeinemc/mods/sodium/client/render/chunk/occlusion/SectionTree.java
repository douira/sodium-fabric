package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.joml.FrustumIntersection;

/**
 * TODO: this can't deal with very high world heights (more than 1024 blocks tall), we'd need multiple tree-cubes for that
 * - make another tree similar to this one that is used to track invalidation cubes in the bfs to make it possible to reuse some of its results (?)
 */
public class SectionTree extends PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    public final Tree mainTree;
    Tree secondaryTree;

    private final int bfsWidth;

    private final float buildDistance;
    protected final int frame;

    public interface VisibleSectionVisitor {
        void visit(int x, int y, int z);
    }

    public SectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType) {
        super(viewport, buildDistance, cullType.isFrustumTested);

        this.bfsWidth = cullType.bfsWidth;
        this.buildDistance = buildDistance;
        this.frame = frame;

        this.mainTree = new Tree(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ);
    }

    protected Tree makeSecondaryTree() {
        // offset diagonally to fully encompass the required area
        return new Tree(this.baseOffsetX + 4, this.baseOffsetY, this.baseOffsetZ + 4);
    }

    public int getFrame() {
        return this.frame;
    }

    public boolean isValidFor(int newCameraSectionX, int newCameraSectionY, int newCameraSectionZ) {
        return this.cameraX >> 4 == newCameraSectionX &&
                this.cameraY >> 4 == newCameraSectionY &&
                this.cameraZ >> 4 == newCameraSectionZ;
    }

    @Override
    public boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return !this.isFrustumTested || super.isWithinFrustum(viewport, section);
    }

    @Override
    public int getOutwardDirections(SectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() + this.bfsWidth ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.getX() - this.bfsWidth ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.getY() + this.bfsWidth ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.getY() - this.bfsWidth ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.getZ() + this.bfsWidth ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() - this.bfsWidth ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        if (!visible) {
            return;
        }

        super.visit(section, true);

        // discard invisible or sections that don't need to be rendered
        if ((section.getRegion().getSectionFlags(section.getSectionIndex()) & RenderSectionFlags.MASK_NEEDS_RENDER) == 0) {
            return;
        }

        this.addToTree(section);
    }

    protected void addToTree(RenderSection section) {
        this.markPresent(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    protected void markPresent(int x, int y, int z) {
        if (this.mainTree.add(x, y, z)) {
            if (this.secondaryTree == null) {
                this.secondaryTree = this.makeSecondaryTree();
            }
            if (this.secondaryTree.add(x, y, z)) {
                throw new IllegalStateException("Failed to add section to trees");
            }
        }
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // check if there's a section at any part of the box
        int minX = SectionPos.posToSectionCoord(x1 - 0.5D);
        int minY = SectionPos.posToSectionCoord(y1 - 0.5D);
        int minZ = SectionPos.posToSectionCoord(z1 - 0.5D);

        int maxX = SectionPos.posToSectionCoord(x2 + 0.5D);
        int maxY = SectionPos.posToSectionCoord(y2 + 0.5D);
        int maxZ = SectionPos.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.isSectionPresent(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isSectionPresent(int x, int y, int z) {
        return this.mainTree.isSectionPresent(x, y, z) ||
                (this.secondaryTree != null && this.secondaryTree.isSectionPresent(x, y, z));
    }

    public void traverseVisible(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        this.mainTree.traverse(visitor, viewport, distanceLimit);
        if (this.secondaryTree != null) {
            this.secondaryTree.traverse(visitor, viewport, distanceLimit);
        }
    }

    public class Tree {
        private static final int INSIDE_FRUSTUM = 0b01;
        private static final int INSIDE_DISTANCE = 0b10;
        private static final int FULLY_INSIDE = 0b11;

        private final long[] tree = new long[64 * 64];
        private final long[] treeReduced = new long[64];
        public long treeDoubleReduced = 0L;
        private final int offsetX, offsetY, offsetZ;

        // set temporarily during traversal
        private int cameraOffsetX, cameraOffsetY, cameraOffsetZ;
        private VisibleSectionVisitor visitor;
        protected Viewport viewport;
        private float distanceLimit;

        public Tree(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        public boolean add(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (x > 63 || y > 63 || z > 63 || x < 0 || y < 0 || z < 0) {
                return true;
            }

            var bitIndex = interleave6x3(x, y, z);
            int reducedBitIndex = bitIndex >> 6;
            int doubleReducedBitIndex = bitIndex >> 12;
            this.tree[reducedBitIndex] |= 1L << (bitIndex & 0b111111);
            this.treeReduced[doubleReducedBitIndex] |= 1L << (reducedBitIndex & 0b111111);
            this.treeDoubleReduced |= 1L << doubleReducedBitIndex;

            return false;
        }

        private static int interleave6x3(int x, int y, int z) {
            return interleave6(x) | interleave6(y) << 1 | interleave6(z) << 2;
        }

        private static int interleave6(int n) {
            n &= 0b000000000000111111;
            n = (n | n << 8) & 0b000011000000001111;
            n = (n | n << 4) & 0b000011000011000011;
            n = (n | n << 2) & 0b001001001001001001;
            return n;
        }

        private static int deinterleave6(int n) {
            n &= 0b001001001001001001;
            n = (n | n >> 2) & 0b000011000011000011;
            n = (n | n >> 4 | n >> 8) & 0b000000000000111111;
            return n;
        }

        boolean isSectionPresent(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (x > 63 || y > 63 || z > 63 || x < 0 || y < 0 || z < 0) {
                return false;
            }

            var bitIndex = interleave6x3(x, y, z);
            int doubleReducedBitIndex = bitIndex >> 12;
            if ((this.treeDoubleReduced & (1L << doubleReducedBitIndex)) == 0) {
                return false;
            }

            int reducedBitIndex = bitIndex >> 6;
            return (this.tree[reducedBitIndex] & (1L << (bitIndex & 0b111111))) != 0;
        }

        public void traverse(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
            this.visitor = visitor;
            this.viewport = viewport;
            this.distanceLimit = distanceLimit;

            var transform = viewport.getTransform();

            // + 1 to section position to compensate for shifted global offset
            this.cameraOffsetX = (transform.intX >> 4) - this.offsetX + 1;
            this.cameraOffsetY = (transform.intY >> 4) - this.offsetY + 1;
            this.cameraOffsetZ = (transform.intZ >> 4) - this.offsetZ + 1;

            // everything is already inside the distance limit if the build distance is smaller
            var initialInside = this.distanceLimit >= SectionTree.this.buildDistance ? INSIDE_DISTANCE : 0;
            this.traverse(0, 0, 0, 0, 5, initialInside);

            this.visitor = null;
            this.viewport = null;
        }

        void traverse(int nodeX, int nodeY, int nodeZ, int nodeOrigin, int level, int inside) {
            int childHalfDim = 1 << (level + 3); // * 16 / 2
            int orderModulator = getChildOrderModulator(nodeX, nodeY, nodeZ, childHalfDim >> 3);
            if ((level & 1) == 1) {
                orderModulator <<= 3;
            }

            if (level <= 1) {
                // check using the full bitmap
                int childOriginBase = nodeOrigin & 0b111111_111111_000000;
                long map = this.tree[nodeOrigin >> 6];

                if (level == 0) {
                    int startBit = nodeOrigin & 0b111111;
                    int endBit = startBit + 8;

                    for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (1L << childIndex)) != 0) {
                            int sectionOrigin = childOriginBase | childIndex;
                            int x = deinterleave6(sectionOrigin) + this.offsetX;
                            int y = deinterleave6(sectionOrigin >> 1) + this.offsetY;
                            int z = deinterleave6(sectionOrigin >> 2) + this.offsetZ;

                            if (inside == FULLY_INSIDE || testLeafNode(x, y, z, inside)) {
                                this.visitor.visit(x, y, z);
                            }
                        }
                    }
                } else {
                    for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (0xFFL << childIndex)) != 0) {
                            this.testChild(childOriginBase | childIndex, childHalfDim, level, inside);
                        }
                    }
                }
            } else if (level <= 3) {
                int childOriginBase = nodeOrigin & 0b111111_000000_000000;
                long map = this.treeReduced[nodeOrigin >> 12];

                if (level == 2) {
                    int startBit = (nodeOrigin >> 6) & 0b111111;
                    int endBit = startBit + 8;

                    for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (1L << childIndex)) != 0) {
                            this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level, inside);
                        }
                    }
                } else {
                    for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (0xFFL << childIndex)) != 0) {
                            this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level, inside);
                        }
                    }
                }
            } else {
                if (level == 4) {
                    int startBit = nodeOrigin >> 12;
                    int endBit = startBit + 8;

                    for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((this.treeDoubleReduced & (1L << childIndex)) != 0) {
                            this.testChild(childIndex << 12, childHalfDim, level, inside);
                        }
                    }
                } else {
                    for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((this.treeDoubleReduced & (0xFFL << childIndex)) != 0) {
                            this.testChild(childIndex << 12, childHalfDim, level, inside);
                        }
                    }
                }
            }
        }

        void testChild(int childOrigin, int childHalfDim, int level, int inside) {
            // calculate section coordinates in tree-space
            int x = deinterleave6(childOrigin);
            int y = deinterleave6(childOrigin >> 1);
            int z = deinterleave6(childOrigin >> 2);

            // immediately traverse if fully inside
            if (inside == FULLY_INSIDE) {
                this.traverse(x, y, z, childOrigin, level - 1, inside);
                return;
            }

            // convert to world-space section origin in blocks, then to camera space
            var transform = this.viewport.getTransform();
            x = ((x + this.offsetX) << 4) - transform.intX;
            y = ((y + this.offsetY) << 4) - transform.intY;
            z = ((z + this.offsetZ) << 4) - transform.intZ;

            boolean visible = true;

            if ((inside & INSIDE_FRUSTUM) == 0) {
                var intersectionResult = this.viewport.getBoxIntersectionDirect(
                        (x + childHalfDim) - transform.fracX,
                        (y + childHalfDim) - transform.fracY,
                        (z + childHalfDim) - transform.fracZ,
                        childHalfDim + OcclusionCuller.CHUNK_SECTION_MARGIN);
                if (intersectionResult == FrustumIntersection.INSIDE) {
                    inside |= INSIDE_FRUSTUM;
                } else {
                    visible = intersectionResult == FrustumIntersection.INTERSECT;
                }
            }

            if ((inside & INSIDE_DISTANCE) == 0) {
                // calculate the point of the node closest to the camera
                int childFullDim = childHalfDim << 1;
                float dx = nearestToZero(x, x + childFullDim) - transform.fracX;
                float dy = nearestToZero(y, y + childFullDim) - transform.fracY;
                float dz = nearestToZero(z, z + childFullDim) - transform.fracZ;

                // check if closest point inside the cylinder
                visible = cylindricalDistanceTest(dx, dy, dz, this.distanceLimit);
                if (visible) {
                    // if the farthest point is also visible, the node is fully inside
                    dx = farthestFromZero(x, x + childFullDim) - transform.fracX;
                    dy = farthestFromZero(y, y + childFullDim) - transform.fracY;
                    dz = farthestFromZero(z, z + childFullDim) - transform.fracZ;

                    if (cylindricalDistanceTest(dx, dy, dz, this.distanceLimit)) {
                        inside |= INSIDE_DISTANCE;
                    }
                }
            }

            if (visible) {
                this.traverse(x, y, z, childOrigin, level - 1, inside);
            }
        }

        boolean testLeafNode(int x, int y, int z, int inside) {
            // input coordinates are section coordinates in world-space

            var transform = this.viewport.getTransform();

            // convert to blocks and move into integer camera space
            x = (x << 4) - transform.intX;
            y = (y << 4) - transform.intY;
            z = (z << 4) - transform.intZ;

            // test frustum if not already inside frustum
            if ((inside & INSIDE_FRUSTUM) == 0 && !this.viewport.isBoxVisibleDirect(
                    (x + 8) - transform.fracX,
                    (y + 8) - transform.fracY,
                    (z + 8) - transform.fracZ,
                    OcclusionCuller.CHUNK_SECTION_RADIUS)) {
                return false;
            }

            // test distance if not already inside distance
            if ((inside & INSIDE_DISTANCE) == 0) {
                // coordinates of the point to compare (in view space)
                // this is the closest point within the bounding box to the center (0, 0, 0)
                float dx = nearestToZero(x, x + 16) - transform.fracX;
                float dy = nearestToZero(y, y + 16) - transform.fracY;
                float dz = nearestToZero(z, z + 16) - transform.fracZ;

                return cylindricalDistanceTest(dx, dy, dz, this.distanceLimit);
            }

            return true;
        }

        static boolean cylindricalDistanceTest(float dx, float dy, float dz, float distanceLimit) {
            // vanilla's "cylindrical fog" algorithm
            // max(length(distance.xz), abs(distance.y))
            return (((dx * dx) + (dz * dz)) < (distanceLimit * distanceLimit)) &&
                    (Math.abs(dy) < distanceLimit);
        }

        @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
        private static int nearestToZero(int min, int max) {
            // this compiles to slightly better code than Math.min(Math.max(0, min), max)
            int clamped = 0;
            if (min > 0) {
                clamped = min;
            }
            if (max < 0) {
                clamped = max;
            }
            return clamped;
        }

        private static int farthestFromZero(int min, int max) {
            int clamped = 0;
            if (min > 0) {
                clamped = max;
            }
            if (max < 0) {
                clamped = min;
            }
            if (clamped == 0) {
                if (Math.abs(min) > Math.abs(max)) {
                    clamped = min;
                } else {
                    clamped = max;
                }
            }
            return clamped;
        }

        int getChildOrderModulator(int x, int y, int z, int childSectionDim) {
            return (x + childSectionDim - this.cameraOffsetX) >>> 31
                    | ((y + childSectionDim - this.cameraOffsetY) >>> 31) << 1
                    | ((z + childSectionDim - this.cameraOffsetZ) >>> 31) << 2;
        }
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk.lists;

public class RegionSectionTree {
    public static long[] makeTree() {
        return new long[4];
    }

    public static void add(long[] tree, int sectionIndex) {
        int treeIndex = sectionIndex >> 6;
        int bitIndex = sectionIndex & 0b111111;
        tree[treeIndex] |= 1L << bitIndex;
    }

    public static int interleave323(int x, int y, int z) {
        // fewer operations than interleaving separately and then combining
        return (x & 0b001) |
                (y & 0b001) << 1 |
                (z & 0b001) << 2 |
                (x & 0b010) << 2 |
                (y & 0b010) << 3 |
                (z & 0b010) << 4 |
                (x & 0b100) << 4 |
                (z & 0b100) << 5;
    }

    public static int deinterleaveX(int n) {
        return (n & 0b1) | (n & 0b1000) >> 2 | (n & 0b1000000) >> 4;
    }

    public static int deinterleaveY(int n) {
        return (n & 0b10) >> 1 | (n & 0b10000) >> 3;
    }

    public static int deinterleaveZ(int n) {
        return (n & 0b100) >> 2 | (n & 0b100000) >> 4 | (n & 0b10000000) >> 5;
    }

    public static int getChildOrderModulatorXYZ(int x, int y, int z, int childFullSectionDim, int cameraSectionOffsetX, int cameraSectionOffsetY, int cameraSectionOffsetZ) {
        return (x + childFullSectionDim - cameraSectionOffsetX) >>> 31
            | ((y + childFullSectionDim - cameraSectionOffsetY) >>> 31) << 1
            | ((z + childFullSectionDim - cameraSectionOffsetZ) >>> 31) << 2;
    }

    public static int getChildOrderModulatorXZ(int x, int z, int childFullSectionDim, int cameraSectionOffsetX, int cameraSectionOffsetZ) {
        return (x + childFullSectionDim - cameraSectionOffsetX) >>> 31
                | ((z + childFullSectionDim - cameraSectionOffsetZ) >>> 31) << 1;
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk;

public class LocalSectionIndex {
    // XZY order
    private static final int X_BITS = 0b111, X_OFFSET = 5;
    private static final int Y_BITS = 0b11, Y_OFFSET = 0;
    private static final int Z_BITS = 0b111, Z_OFFSET = 2;

    public static int pack(int x, int y, int z) {
        return ((x & X_BITS) << X_OFFSET) | ((y & Y_BITS) << Y_OFFSET) | ((z & Z_BITS) << Z_OFFSET);
    }
}
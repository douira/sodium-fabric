package me.jellysquid.mods.sodium.client.render.measurement;

import java.util.concurrent.atomic.AtomicLong;

public enum Counter {
    UNIQUE_TRIGGERS,

    QUADS,
    BSP_SECTIONS,

    COMPRESSION_CANDIDATES,
    COMPRESSION_SUCCESS,
    COMPRESSED_SIZE,
    UNCOMPRESSED_SIZE,

    HEURISTIC_BOUNDING_BOX,
    HEURISTIC_OPPOSING_UNALIGNED,
    HEURISTIC_BSP_OPPOSING_UNALIGNED;

    private final AtomicLong value = new AtomicLong();

    public long getValue() {
        return this.value.get();
    }

    public void incrementBy(long amount) {
        this.value.addAndGet(amount);
    }

    public void increment() {
        this.incrementBy(1);
    }

    public boolean isPositive() {
        return this.getValue() > 0;
    }

    public static void resetAll() {
        for (var counter : values()) {
            counter.value.set(0);
        }
    }
}

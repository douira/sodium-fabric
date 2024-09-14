package net.caffeinemc.mods.sodium.client.render.measurement;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;

public enum Counter {
    // misc
    UPLOADS_WITHOUT_GRAPH_UPDATE_REQUIRED,

    // general counts
    UNIQUE_TRIGGERS,
    QUADS,
    BSP_SECTIONS,

    // compression
    COMPRESSION_CANDIDATES,
    COMPRESSION_SUCCESS,
    COMPRESSED_SIZE,
    UNCOMPRESSED_SIZE,

    // heuristic usage
    HEURISTIC_BOUNDING_BOX(HeuristicType.ALIGNED_TO_BOUNDING_BOX),
    HEURISTIC_OPPOSING_UNALIGNED(HeuristicType.SNR_UNALIGNED),
    HEURISTIC_BSP_OPPOSING_UNALIGNED(HeuristicType.SNR_UNALIGNED);

    private static final EnumMap<HeuristicType, Counter> HEURISTIC_COUNTERS = new EnumMap<>(HeuristicType.class);

    static {
        for (Counter counter : values()) {
            if (counter.heuristicType != null) {
                HEURISTIC_COUNTERS.put(counter.heuristicType, counter);
            }
        }
    }

    private final AtomicLong value = new AtomicLong();
    private final HeuristicType heuristicType;

    private Counter() {
        this(null);
    }

    private Counter(HeuristicType type) {
        this.heuristicType = type;
    }

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

    public static Counter fromHeuristicType(HeuristicType type) {
        return HEURISTIC_COUNTERS.get(type);
    }
}

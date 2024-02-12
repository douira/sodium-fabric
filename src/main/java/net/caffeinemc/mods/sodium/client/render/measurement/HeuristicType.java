package net.caffeinemc.mods.sodium.client.render.measurement;

/**
 * A finer distinction of the sort types for debugging purposes.
 */
public enum HeuristicType {
    EMPTY,
    SINGLE_PLANE,
    OPPOSING_ALIGNED,
    ALIGNED_TO_BOUNDING_BOX,

    SNR_ALIGNED,
    SNR_UNALIGNED,
    SNR_MIXED,

    STATIC_TOPO,

    BSP,
    DYNAMIC_TOPO,
    DYNAMIC_DISTANCE,
    DYNAMIC_DISTANCE_TOPO,
}

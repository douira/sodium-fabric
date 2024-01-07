package me.jellysquid.mods.sodium.client.render.measurement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public class Measurement {
    public static final boolean DEBUG_ONLY_TOPO_OR_DISTANCE_SORT = false;
    public static final boolean DEBUG_SKIP_TOPO_SORT = false;
    public static final boolean DEBUG_COMPRESSION_STATS = false;
    public static final boolean DEBUG_TRIGGER_STATS = false;
    public static final boolean DEBUG_DISABLE_FRUSTUM_CULLING = false;
    public static final boolean DEBUG_ONLY_RENDER_CURRENT_SECTION = false;

    private static final boolean AUTO_RELOAD_WORLD = false;

    static final Logger LOGGER = LogManager.getLogger(Measurement.class);

    private static int framesWithoutGraphUpdate = 0;
    private static ReferenceArrayList<TimingRecorder> recorders = new ReferenceArrayList<>();

    public static void registerFrame(boolean graphHasUpdate) {
        if (graphHasUpdate) {
            framesWithoutGraphUpdate = 0;
        } else {
            framesWithoutGraphUpdate++;
        }
    }

    public static boolean shouldReloadWorld() {
        return framesWithoutGraphUpdate > 300 && AUTO_RELOAD_WORLD;
    }

    static void registerRecorder(TimingRecorder recorder) {
        recorders.add(recorder);
    }

    public static void reset() {
        framesWithoutGraphUpdate = 0;

        for (var recorder : recorders) {
            recorder.resetAfterWarmup();
        }

        for (var counter : Counter.values()) {
            LOGGER.info(counter + ": " + counter.getValue());
        }

        if (Counter.UNIQUE_TRIGGERS.isPositive()
                && Counter.QUADS.isPositive()
                && Counter.BSP_SECTIONS.isPositive()) {
            LOGGER.info("Triggers per quad: " +
                    ((double) Counter.UNIQUE_TRIGGERS.getValue() / Counter.QUADS.getValue()));
            LOGGER.info("Triggers per section: " +
                    ((double) Counter.UNIQUE_TRIGGERS.getValue() / Counter.BSP_SECTIONS.getValue()));
        }
        if (Counter.COMPRESSION_CANDIDATES.isPositive()
                && Counter.COMPRESSION_SUCCESS.isPositive()
                && Counter.COMPRESSED_SIZE.isPositive()
                && Counter.UNCOMPRESSED_SIZE.isPositive()) {
            LOGGER.info("Compressed size ratio: " +
                    ((double) Counter.COMPRESSED_SIZE.getValue() / Counter.UNCOMPRESSED_SIZE.getValue()));
            LOGGER.info("Compression success ratio: " +
                    ((double) Counter.COMPRESSION_SUCCESS.getValue()
                            / Counter.COMPRESSION_CANDIDATES.getValue()));
        }

        Counter.resetAll();
    }
}

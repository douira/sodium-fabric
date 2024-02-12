package net.caffeinemc.mods.sodium.client.render.measurement;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

/**
 * Compression results on 1992 sections:
 * compression candidates 55084, compression performed 1202 (ratio: 2.1%)
 * uncompressed size 397665, compressed size 170944 (ratio: 42.9%)
 * Removing the compression minimum size results in a total compression ratio of
 * 34% and a 92% success rate. This isn't much of an improvement, it seems the
 * large candidates make up most of the compressible data. Increasing the
 * minimum size to 16 lowers the success rate to 3.4% while the total
 * compression ratio is 39%.
 * 
 * test scenario: test world, 1991 events, total 538121 quads, 32 rd, 15 chunk
 * builder threads
 *
 * at 128406c9743eab8ec90dfceeac34af6fe932af97
 * (baseline):
 * sort 15-23ns per quad avg, build 230-233ns per quad avg
 * 
 * at 2aabb0a7a6a54f139db3cc2beb83881219561678
 * (with compression of interval points as longs):
 * sort 15-23ns per quad avg, build 150-165ns per quad avg
 * 
 * at 4f1fb35495b3e5adab2bbc9823cbd6cbf2e5b438
 * (with sorting compressed interval points as longs):
 * sort 15-23ns per quad avg, build 130-140ns per quad avg
 * 
 * at d4f220080c2bf980e8f920d4ad96e4c8be465db1
 * (fixed child partition planes not being added to workspace on node reuse):
 * rebuild with node reuse 120ns per quad avg,
 * rebuild without node reuse 202ns per quad avg
 * previously it was more like 105ns per quad avg but the child partition planes
 * were missing (though it wasn't noticeable in many situations)
 * 
 * typical heuristic values for hermitcraft 7 world:
 * HEURISTIC_BOUNDING_BOX: 21
 * HEURISTIC_OPPOSING_UNALIGNED: 17
 * HEURISTIC_BSP_OPPOSING_UNALIGNED: 14194
 * This happens because fluid render products quads that have an aligned normal
 * but don't have an aligned facing because they're just slightly slanted.
 * 
 * Distance sorting speeds on frozen ocean ice test with auto reload
 * Arrays.sort with long packing: 59ns per quad avg after 50 epochs
 * merge and radix sort: 40ns per quad avg after 50 epochs
 * 
 * Distance vs BSP sorting speed (tests distance sort when BSP sorting):
 * after 50 epochs, 32 rd, 15 threads, 547 translucent quads per section avg
 * BSP sorting: 23ns per quad, distance sorting: 38ns per quad
 */
public class Measurement {
    public static final boolean DEBUG_NO_BSP = false;
    public static final boolean DEBUG_SKIP_TOPO_SORT = false;
    public static final boolean DEBUG_COMPRESSION_STATS = false;
    public static final boolean DEBUG_TRIGGER_STATS = false;
    public static final boolean DEBUG_DISABLE_FRUSTUM_CULLING = false;
    public static final boolean DEBUG_DISABLE_OCCLUSION_CULLING = false;
    public static final boolean DEBUG_ONLY_RENDER_CURRENT_SECTION = false;
    public static final boolean DEBUG_BSP_DIRECT_SORT_COMPARISON = false;
    public static final HeuristicType DEBUG_ONLY_RENDER_TYPE = null;
    public static final int DEBUG_REDUCE_RENDER_INTERVAL = 0;
    public static final boolean DEBUG_FREEZE_WORLD = true;

    private static final boolean AUTO_RELOAD_WORLD = false;
    private static final int EPOCH_COUNT_TARGET = 50;
    private static final boolean PER_EPOCH_MEASUREMENTS = false;

    static final Logger LOGGER = LogManager.getLogger(Measurement.class);

    private enum ResetState {
        INITIAL,
        WARMUP,
        MEASUREMENT
    }

    private int framesWithoutGraphUpdate = 0;
    private final ReferenceArrayList<TimingRecorder> recorders = new ReferenceArrayList<>();
    private int currentEpoch = 0;
    private ResetState resetState = ResetState.INITIAL;
    private long measurementStartTime;

    public static Measurement instance() {
        return SodiumWorldRenderer.instance().measurement;
    }

    public void registerFrame(boolean graphHasUpdate) {
        if (graphHasUpdate) {
            this.framesWithoutGraphUpdate = 0;
        } else {
            this.framesWithoutGraphUpdate++;
        }
    }

    public boolean shouldReloadWorld() {
        return this.framesWithoutGraphUpdate > 300 && AUTO_RELOAD_WORLD;
    }

    void registerRecorder(TimingRecorder recorder) {
        this.recorders.add(recorder);
    }

    public void reset() {
        if (DEBUG_FREEZE_WORLD) {
            Minecraft.getInstance().level.tickRateManager().setFrozen(true);
        }

        if (this.resetState == ResetState.INITIAL) {
            LOGGER.info("Started measurement");
            this.resetState = ResetState.WARMUP;
            return;
        }

        this.framesWithoutGraphUpdate = 0;

        // check if all this.recorders have been warmed up
        int warmedUpCount = 0;
        for (var recorder : this.recorders) {
            if (recorder.checkWarmup()) {
                warmedUpCount++;
            }
        }

        if (warmedUpCount < this.recorders.size()) {
            LOGGER.info(warmedUpCount + " out of " + this.recorders.size() + " recorders have been warmed up");

            for (var recorder : this.recorders) {
                recorder.reset();
            }
        } else if (this.resetState == ResetState.WARMUP) {
            // message on initial warmup and set reset flag for initial reset
            LOGGER.info("All recorders have been warmed up. Starting measurement.");
            this.resetState = ResetState.MEASUREMENT;
            this.currentEpoch = 1;
            this.measurementStartTime = System.nanoTime();

            for (var recorder : this.recorders) {
                recorder.reset();
            }
        } else {
            LOGGER.info(this.currentEpoch + " epochs complete");
            if (EPOCH_COUNT_TARGET > 0) {
                var elapsed = System.nanoTime() - this.measurementStartTime;
                var epochTime = elapsed / this.currentEpoch;
                var remaining = Duration.ofNanos(epochTime * (EPOCH_COUNT_TARGET - this.currentEpoch));
                LOGGER.info((EPOCH_COUNT_TARGET - this.currentEpoch) + " epochs remaining, " +
                        "estimated time remaining: " + remaining);
            }

            for (var recorder : this.recorders) {
                recorder.print();

                if (PER_EPOCH_MEASUREMENTS) {
                    recorder.reset();
                }
            }

            this.currentEpoch++;

            printAndResetCounters();
        }
    }

    private void printAndResetCounters() {
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

package me.jellysquid.mods.sodium.client.render.measurement;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

/**
 * Compression results on 1992 sections:
 * compression candidates 55084, compression performed 1202 (ratio: 2.1%)
 * uncompressed size 397665, compressed size 170944 (ratio: 42.9%)
 * Removing the compresson minimum size results in a total compression ratio of
 * 34% and a 92% success rate. This isn't much of an improvement, it seems the
 * large candidates make up most of the compressable data. Increasing the
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
 */
public class TimingRecorder {
    static record TimedEvent(int size, long ns) {
    }

    private static final int WARMUP_COUNT = 500;

    private ReferenceArrayList<TimedEvent> events = new ReferenceArrayList<>(1000);
    private boolean warmedUp = false;

    private final String name;
    private int remainingWarmup;
    private boolean printEvents;
    private boolean printData;

    public TimingRecorder(String name, int warmupCount, boolean printEvents) {
        this.name = name;
        this.remainingWarmup = warmupCount;
        this.printEvents = printEvents;

        Measurement.registerRecorder(this);
    }

    public TimingRecorder(String name, int warmupCount) {
        this(name, warmupCount, false);
    }

    public TimingRecorder(String name) {
        this(name, WARMUP_COUNT);
    }

    public void recordNow(int size, long startNanos) {
        this.recordDelta(size, System.nanoTime() - startNanos);
    }

    public void recordDelta(int size, long startNanos, long endNanos) {
        this.recordDelta(size, endNanos - startNanos);
    }

    synchronized public void recordDelta(int size, long delta) {
        if (!this.warmedUp) {
            this.remainingWarmup--;
            if (this.remainingWarmup == 0) {
                Measurement.LOGGER.info("Warmed up recorder " + this.name);
            }
            return;
        }

        this.events.add(new TimedEvent(size, delta));

        if (this.printEvents) {
            Measurement.LOGGER.info("Event for " + this.name + ": " + size + " quads, " + delta + "ns " +
                    "(" + (delta / size) + "ns per quad)");
        }
    }

    public void print() {
        var builder = new StringBuilder();
        builder.append("size,ns\n");

        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        long totalSize = 0;

        for (var event : this.events) {
            if (this.printData) {
                builder.append(event.size).append(",").append(event.ns).append(";");
            }
            totalTime += event.ns;
            minTime = Math.min(minTime, event.ns);
            maxTime = Math.max(maxTime, event.ns);
            totalSize += event.size;
        }

        int eventCount = this.events.size();
        Measurement.LOGGER.info("Timings for " + this.name + ":");
        Measurement.LOGGER.info("min " + minTime +
                "ns, max " + maxTime +
                "ns, avg " + (totalTime / eventCount) +
                "ns, total " + totalTime +
                "ns (" + (totalTime / 1000000) +
                "ms). Total size " + totalSize +
                ", avg size " + (totalSize / eventCount) +
                ". Avg time per quad " + (totalTime / totalSize) +
                "ns. Avg quads per event " + (totalSize / eventCount) +
                ". " + eventCount + " events.");

        if (this.printData) {
            Measurement.LOGGER.info(builder.toString());
        }
    }

     void resetAfterWarmup() {
        if (this.remainingWarmup <= 0) {
            if (!this.events.isEmpty()) {
                this.print();
            }

            this.warmedUp = true;
            Measurement.LOGGER.info("Started recorder " + this.name);
        }

        this.events.clear();
    }
}

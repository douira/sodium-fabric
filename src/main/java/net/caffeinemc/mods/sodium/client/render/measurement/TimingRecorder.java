package net.caffeinemc.mods.sodium.client.render.measurement;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public class TimingRecorder {
    record TimedEvent(int size, long ns) {
    }

    private static final int WARMUP_COUNT = 5000;

    private final ReferenceArrayList<TimedEvent> events = new ReferenceArrayList<>(1000);
    private boolean warmedUp = false;

    private final String name;
    private final boolean printEvents;
    private static final boolean PRINT_DATA = false;
    private int remainingWarmup;
    private boolean receivedEvents;

    public TimingRecorder(String name, int warmupCount, boolean printEvents) {
        this.name = name;
        this.remainingWarmup = warmupCount;
        this.printEvents = printEvents;

        Measurement.instance().registerRecorder(this);
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
        this.receivedEvents = true;
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

    private boolean isEmpty() {
        return this.events.isEmpty();
    }

    public void print() {
        if (this.isEmpty()) {
            return;
        }

        var builder = new StringBuilder();
        builder.append("size,ns\n");

        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        long totalSize = 0;

        for (var event : this.events) {
            if (PRINT_DATA) {
                builder.append(event.size).append(",").append(event.ns).append(";");
            }
            totalTime += event.ns;
            minTime = Math.min(minTime, event.ns);
            maxTime = Math.max(maxTime, event.ns);
            totalSize += event.size;
        }

        int eventCount = this.events.size();
        if (eventCount == 0 || totalSize == 0) {
            Measurement.LOGGER.info("No events or total size 0 for " + this.name);
        } else {
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
        }

        if (PRINT_DATA) {
            Measurement.LOGGER.info(builder.toString());
        }
    }

    boolean checkWarmup() {
        if (!this.warmedUp && this.remainingWarmup <= 0) {
            this.warmedUp = true;
        }
        return this.warmedUp || !this.receivedEvents;
    }

    void reset() {
        this.events.clear();
        Measurement.LOGGER.info("Started recorder " + this.name);
    }
}

package net.caffeinemc.mods.sodium.client.util;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceRBTreeMap;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages cache resources and evicts them based on their age and the memory usage.
 * - each sections gets to stay a minimum time in the cache
 * - the maximum time is long if there is low memory usage
 * - the eviction time interpolates between the minimum and infinity based on the memory usage
 * <p>
 * TODO: handle key collision in timedFreeMap, never seems to actually happen though
 * TODO: clear items from sizeToCached to avoid wasting memory if the queue for a particular queue is never iterated again? (removing each one when it gets evicted is slow as the queues are linked lists) maybe spend constant effort each time clearing out the queues for the sizes of buffers that were just evicted
 * TODO: very rarely a mesh buffer seems to leak, this might actually be a failure of the cache and not the user of the cache. If this never happens with index buffers though, that would point to this issue being external to the cache.
 */
public class BufferCache {
    private static final long MINIMUM_RETENTION_TIME = 5_000_000L; // 5ms
    private static final long MAXIMUM_RETENTION_TIME = 20_000_000_000L; // 20s
    private static final long MAX_TARGET_MEMORY_USAGE = 100_000_000L; // 100MB
    private static final double MEMORY_USAGE_FACTOR_EXPONENT = 0.25d;

    // totalSize and timedFreeMap are updated by the main thread using the removeFromMap and addToMap queues.
    // removeFromMap, addToMap, and sizeToCached can be concurrently updated by any thread.
    // sizeToCached is always up-to-date and is updated concurrently.
    private final Long2ReferenceRBTreeMap<CachedNativeBuffer> timedFreeMap = new Long2ReferenceRBTreeMap<>();
    private volatile ConcurrentLinkedQueue<CachedNativeBuffer> addToMap = new ConcurrentLinkedQueue<>();
    private final Int2ReferenceOpenHashMap<ConcurrentLinkedQueue<CachedNativeBuffer>> sizeToCached = new Int2ReferenceOpenHashMap<>();
    private final AtomicLong cachedSize = new AtomicLong();
    private final AtomicInteger cachedCount = new AtomicInteger();

    private void addCachedSize(CachedNativeBuffer buffer) {
        this.cachedSize.addAndGet(buffer.getLength());
        this.cachedCount.incrementAndGet();
    }

    private void removeCachedSize(CachedNativeBuffer buffer) {
        this.cachedSize.addAndGet(-buffer.getLength());
        this.cachedCount.decrementAndGet();
    }

    public void evictOutdatedResources() {
        long now = System.nanoTime();

        // mitigate modifications while iterating by swapping queues
        var addToMap = this.addToMap;
        this.addToMap = new ConcurrentLinkedQueue<>();
        while (!addToMap.isEmpty()) {
            var resource = addToMap.poll();
            if (resource.isNotInUse()) {
                this.timedFreeMap.put(resource.getLastUse(), resource);
            }
        }

        // evict outdated resources
        var evictOlderThan = getEvictOlderThan(now);
        var toEvictIt = this.timedFreeMap.headMap(evictOlderThan).values().iterator();
        while (toEvictIt.hasNext()) {
            var buffer = toEvictIt.next();
            toEvictIt.remove();

            // evict if this is actually the right last use time
            if (buffer.getLastUse() < evictOlderThan && buffer.attemptSetFreed()) {
                this.removeCachedSize(buffer);
                buffer.free();
            }
        }
    }

    private long getEvictOlderThan(long now) {
        // interpolation between the minimum and maximum eviction time based on the memory usage
        double memoryUsageFactor;
        if (this.cachedSize.get() >= MAX_TARGET_MEMORY_USAGE) {
            memoryUsageFactor = 0.0;
        } else {
            var memoryUsage = (double) this.cachedSize.get() / MAX_TARGET_MEMORY_USAGE;
            memoryUsageFactor = 1 - Math.pow(memoryUsage, MEMORY_USAGE_FACTOR_EXPONENT);
        }
        long retentionTime = MINIMUM_RETENTION_TIME + (long) (memoryUsageFactor * (MAXIMUM_RETENTION_TIME - MINIMUM_RETENTION_TIME));
        return now - retentionTime;
    }

    public CachedNativeBuffer acquire(int capacity) {
        // check for matching buffer in the cache
        var now = System.nanoTime();
        var queue = this.sizeToCached.get(capacity);
        if (queue != null) {
            CachedNativeBuffer buffer;
            while ((buffer = queue.poll()) != null) {
                if (buffer.attemptSetInUse(now)) {
                    this.removeCachedSize(buffer);

                    // remove the queue if it's empty
                    if (queue.isEmpty()) {
                        synchronized (this.sizeToCached) {
                            if (queue.isEmpty()) {
                                this.sizeToCached.remove(capacity);
                            }
                        }
                    }

                    return buffer;
                }
            }
        }

        // create a new buffer
        return new CachedNativeBuffer(capacity, now);
    }

    public void release(CachedNativeBuffer buffer) {
        buffer.releaseNow(System.nanoTime());
        this.addCachedSize(buffer);
        this.addToMap.add(buffer);

        // put it in a cache queue, make a new queue if necessary
        var capacity = buffer.getLength();
        var queue = this.sizeToCached.get(capacity);
        if (queue == null) {
            synchronized (this.sizeToCached) {
                queue = this.sizeToCached.get(capacity);
                if (queue == null) {
                    queue = new ConcurrentLinkedQueue<>();
                    this.sizeToCached.put(capacity, queue);
                }
            }
        }

        queue.add(buffer);
    }

    public void freeBufferInUse(CachedNativeBuffer buffer) {
        // only valid for buffers that are currently in use
        // as it doesn't remove them from the cache if they aren't in use
        if (buffer.isNotInUse()) {
            this.removeCachedSize(buffer);
        }
        buffer.free();
    }

    public void destroy() {
        for (var queue : this.sizeToCached.values()) {
            for (var buffer : queue) {
                buffer.free();
            }
        }
    }

    public static BufferCache instance() {
        return SodiumWorldRenderer.instance().bufferCache;
    }

    public void addDebugStrings(Collection<String> list) {
        list.add("Buffer Cache: %04dMB (%03d)".formatted(this.cachedSize.get() / 1_000_000, this.cachedCount.get()));
    }
}

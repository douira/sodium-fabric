package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.resource;

import it.unimi.dsi.fastutil.longs.Long2ReferenceRBTreeMap;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages cache resources and evicts them based on their age and the memory usage.
 * - each sections gets to stay a minimum time in the cache
 * - the maximum time is long if there is low memory usage
 * - the eviction time interpolates between the minimum and infinity based on the memory usage
 */
public class CachedResourceManager {
    private static final long MINIMUM_RETENTION_TIME = 5_000_000L; // 5ms
    private static final long MAXIMUM_RETENTION_TIME = 20_000_000_000L; // 20s
    private static final long MAX_TARGET_MEMORY_USAGE = 100_000_000L; // 100MB
    private static final double MEMORY_USAGE_FACTOR_EXPONENT = 1d / 4;

    private long totalSize = 0;
    private final Long2ReferenceRBTreeMap<CachedResource> cachedResources = new Long2ReferenceRBTreeMap<>();
    private volatile ConcurrentLinkedQueue<CachedResource> releasedResources = new ConcurrentLinkedQueue<>();
    private volatile ConcurrentLinkedQueue<CachedResource> acquiredResources = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<CachedResource> lastReleasedResources;
    private ConcurrentLinkedQueue<CachedResource> lastAcquiredResources;

    public void evictOutdatedResources() {
        long now = System.nanoTime();

        // swap out the released and acquired resources to avoid concurrent modification
        var released = this.releasedResources;
        var newReleased = this.lastReleasedResources;
        if (newReleased == null) {
            newReleased = new ConcurrentLinkedQueue<>();
        }
        this.releasedResources = newReleased;
        var acquired = this.acquiredResources;
        var newDeleted = this.lastAcquiredResources;
        if (newDeleted == null) {
            newDeleted = new ConcurrentLinkedQueue<>();
        }
        this.acquiredResources = newDeleted;

        // process creations and deletions, updating the tree and total size
        for (var resource : acquired) {
            if (this.cachedResources.remove(resource.lastUse) != null) {
                this.totalSize -= resource.size;
            }
        }

        for (var resource : released) {
            var toPut = this.cachedResources.put(resource.lastUse, resource);

            // stop if the resource was already in the map
            if (toPut == resource) {
                continue;
            }

            // probe the map to avoid collisions
            while (toPut != null) {
                toPut.lastUse++;
                toPut = this.cachedResources.put(toPut.lastUse, toPut);
            }

            this.totalSize += resource.size;
        }

        released.clear();
        acquired.clear();
        this.lastReleasedResources = released;
        this.lastAcquiredResources = acquired;

        // evict outdated resources
        var toEvict = this.cachedResources.headMap(getEvictUsedBefore(now));
        var toEvictIt = toEvict.values().iterator();
        while (toEvictIt.hasNext()) {
            var resource = toEvictIt.next();
            toEvictIt.remove();

            // attempt deletion but only update the size if a change actually happened.
            if (resource.deleteResource()) {
                this.totalSize -= resource.size;
            }
        }
    }

    private long getEvictUsedBefore(long now) {
        // interpolation between the minimum and maximum eviction time based on the memory usage
        double memoryUsageFactor;
        if (this.totalSize >= MAX_TARGET_MEMORY_USAGE) {
            memoryUsageFactor = 0.0;
        } else {
            var memoryUsage = (double) this.totalSize / MAX_TARGET_MEMORY_USAGE;
            memoryUsageFactor = 1 - Math.pow(memoryUsage, MEMORY_USAGE_FACTOR_EXPONENT);
        }
        long retentionTime = MINIMUM_RETENTION_TIME + (long) (memoryUsageFactor * (MAXIMUM_RETENTION_TIME - MINIMUM_RETENTION_TIME));
        return now - retentionTime;
    }

    void notifyResourceReleased(CachedResource resource) {
        this.releasedResources.add(resource);
    }

    void notifyResourceAcquired(CachedResource resource) {
        this.acquiredResources.add(resource);
    }

    public static CachedResourceManager instance() {
        return SodiumWorldRenderer.instance().cachedResourceManager;
    }

    public void addDebugStrings(Collection<String> list) {
        list.add("TS Cache: %05dMB (%05d)".formatted(this.totalSize / 1_000_000, this.cachedResources.size()));
    }
}

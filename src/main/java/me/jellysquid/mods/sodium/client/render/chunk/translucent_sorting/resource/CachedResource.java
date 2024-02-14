package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.resource;

import java.util.concurrent.Semaphore;

/**
 * The resource interface holds the actual resource while the instance of this class is notified of usage events and can decide when to destroy the resource. It is not allowed to destroy the resource while it is in use. The beginning of a usage period should always be signaled by a call to {@link #acquire()} and ended by a call to {@link #release()}.
 */
public class CachedResource {
    private ResourceInterface container;
    final int size;
    long lastUse;
    private final Semaphore semaphore = new Semaphore(0); // acquire permit to use resource
    private final CachedResourceManager manager;

    public CachedResource(ResourceInterface container, CachedResourceManager manager) {
        this.container = container;
        this.manager = manager;
        this.size = container.getSize();
    }

    public CachedResource(ResourceInterface container) {
        this(container, CachedResourceManager.instance());
    }

    boolean deleteResource() {
        if (this.containerIsDeleted()) {
            return false;
        }

        // acquire permit to delete resource and then release if successful
        if (this.semaphore.tryAcquire()) {
            var resourceIsPresent = this.container.isResourcePresent();
            if (resourceIsPresent) {
                this.container.deleteResource();
            }
            this.semaphore.release();
            return resourceIsPresent;
        }
        return false;
    }

    public void acquire() {
        if (this.containerIsDeleted()) {
            return;
        }

        // acquire permit to use resource and release it when done
        this.semaphore.acquireUninterruptibly();
        if (!this.container.isResourcePresent()) {
            this.container.createResource();

            // only notify if the resource was actually created,
            // otherwise it would have already been tracked as cached
            this.manager.notifyResourceAcquired(this);
        }
    }

    public void release() {
        this.semaphore.release();

        // mark own thread id in the lower bits to avoid conflicts with other threads
        var now = System.nanoTime();
        this.lastUse = now & 0xFFFF_FFFF_FFFF_FF00L | Thread.currentThread().getId() & 0xFF;

        this.manager.notifyResourceReleased(this);
    }

    public void initialReleaseWithoutSignal() {
        if (this.containerIsDeleted()) {
            return;
        }

        // no signalling because it will get acquired immediately after this
        if (this.container.isResourcePresent()) {
            this.semaphore.release();
        }
    }

    public void destroy() {
        // notify as acquired because it will be deleted by other means now
        this.manager.notifyResourceAcquired(this);
        this.container = null;
    }

    boolean containerIsDeleted() {
        return this.container == null;
    }
}

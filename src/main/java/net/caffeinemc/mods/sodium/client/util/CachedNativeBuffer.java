package net.caffeinemc.mods.sodium.client.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class CachedNativeBuffer extends NativeBuffer {
    private long lastUse;
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    public CachedNativeBuffer(int capacity, long now) {
        super(capacity);
        this.attemptSetInUse(now);
    }

    boolean attemptSetInUse(long now) {
        var success = this.inUse.compareAndSet(false, true);
        if (success) {
            this.lastUse = now;
        }
        return success;
    }

    boolean attemptSetFreed() {
        // marks it as in use but then frees it which gets rid of the object altogether
        return this.inUse.compareAndSet(false, true);
    }

    void releaseNow(long now) {
        this.lastUse = now;
        this.inUse.set(false);
    }

    @Override
    public void free() {
        this.inUse.set(true);
        super.free();
    }

    boolean isNotInUse() {
        return !this.inUse.get();
    }

    long getLastUse() {
        return this.lastUse;
    }
}

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
        // set the last use time even if something else is using it.
        // this is fine because upon release, the last use time is updated again
        this.lastUse = now;
        return this.inUse.compareAndSet(false, true);
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

    boolean isInUse() {
        return this.inUse.get();
    }

    long getLastUse() {
        return this.lastUse;
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk.compile;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public abstract class BuilderTaskOutput {
    public final RenderSection render;
    public final int submitTime;
    private boolean disposed;

    public BuilderTaskOutput(RenderSection render, int buildTime) {
        this.render = render;
        this.submitTime = buildTime;
    }

    public void destroy() {
        this.disposed = true;
    }

    /**
     * This method is called after the contents of this output have been uploaded to the GPU. It internally calls {@link #softDestroy()} because sometimes this is called even after the output has already been destroyed.
     */
    public void softDestroySafe() {
        if (!this.disposed) {
            this.softDestroy();
        }
    }

    protected void softDestroy() {
    }
}

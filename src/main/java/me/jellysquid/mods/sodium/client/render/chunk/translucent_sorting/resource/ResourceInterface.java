package me.jellysquid.mods.sodium.client.render.chunk.translucent_sorting.resource;

public interface ResourceInterface {
    void createResource();

    boolean isResourcePresent();

    void deleteResource();

    int getSize();
}

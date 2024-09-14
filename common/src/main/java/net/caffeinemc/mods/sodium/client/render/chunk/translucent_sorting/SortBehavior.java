package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting;

import net.caffeinemc.mods.sodium.client.gui.options.TextProvider;
import net.minecraft.network.chat.Component;

// NOTE: detailed sort behavior selection removed from setting screen during merge
public enum SortBehavior implements TextProvider {
    OFF("options.off", "OFF", SortMode.NONE),
    STATIC("sodium.options.sort_behavior.reduced", "S", SortMode.STATIC),
    DYNAMIC_DEFER_ALWAYS("sodium.options.sort_behavior.df", "DF", PriorityMode.NONE, DeferMode.ALWAYS),
    DYNAMIC_DEFER_NEARBY_ONE_FRAME("sodium.options.sort_behavior.n1", "N1", PriorityMode.NEARBY, DeferMode.ONE_FRAME),
    DYNAMIC_DEFER_NEARBY_ZERO_FRAMES("sodium.options.sort_behavior.n0", "N0", PriorityMode.NEARBY, DeferMode.ZERO_FRAMES),
    DYNAMIC_DEFER_ALL_ONE_FRAME("sodium.options.sort_behavior.a1", "A1", PriorityMode.ALL, DeferMode.ONE_FRAME),
    DYNAMIC_DEFER_ALL_ZERO_FRAMES("sodium.options.sort_behavior.a0", "A0", PriorityMode.ALL, DeferMode.ZERO_FRAMES);

    private final Component name;
    private final String shortName;
    private final SortMode sortMode;
    private final PriorityMode priorityMode;
    private final DeferMode deferMode;

    SortBehavior(String name, String shortName, SortMode sortMode, PriorityMode priorityMode, DeferMode deferMode) {
        this.name = Component.translatable(name);
        this.shortName = shortName;
        this.sortMode = sortMode;
        this.priorityMode = priorityMode;
        this.deferMode = deferMode;
    }

    SortBehavior(String name, String shortName, SortMode sortMode) {
        this(name, shortName, sortMode, null, null);
    }

    SortBehavior(String name, String shortName, PriorityMode priorityMode, DeferMode deferMode) {
        this(name, shortName, SortMode.DYNAMIC, priorityMode, deferMode);
    }

    @Override
    public Component getLocalizedName() {
        return this.name;
    }

    public String getShortName() {
        return this.shortName;
    }

    public SortMode getSortMode() {
        return this.sortMode;
    }

    public PriorityMode getPriorityMode() {
        return this.priorityMode;
    }

    public DeferMode getDeferMode() {
        return this.deferMode;
    }

    public enum SortMode {
        NONE, STATIC, DYNAMIC
    }

    public enum PriorityMode {
        NONE, NEARBY, ALL
    }

    public enum DeferMode {
        ALWAYS, ONE_FRAME, ZERO_FRAMES
    }
}

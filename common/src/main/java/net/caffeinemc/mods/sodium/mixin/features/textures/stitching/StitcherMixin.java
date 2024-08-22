package net.caffeinemc.mods.sodium.mixin.features.textures.stitching;

import net.caffeinemc.mods.sodium.client.render.texture.StitcherHolderExtension;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(Stitcher.class)
public class StitcherMixin<T extends Stitcher.Entry> {
    @Shadow
    @Final
    private List<Stitcher.Holder<T>> texturesToBeStitched;

    @Shadow
    @Final
    private List<Stitcher.Region<T>> storage;

    @Shadow
    private int storageX;

    @Shadow
    private int storageY;

    @Shadow
    @Final
    private int mipLevel;
    @Unique
    private static final Comparator<Stitcher.Holder<?>> POT_HOLDER_COMPARATOR = Comparator
            .<Stitcher.Holder<?>>comparingInt(holder -> ((StitcherHolderExtension) (Object) holder).atlasSize()).reversed()
            .thenComparing(holder -> holder.entry().name());

    /**
     * @author douira
     * @reason Return power of two (POT) value for sprite holder dimensions
     */
//    @Overwrite
//    static int smallestFittingMinTexel(int dimension, int mipLevel) {
//        return Mth.smallestEncompassingPowerOfTwo(dimension >> mipLevel);
//    }

    /**
     * @author douira
     * @reason Stitch more efficiently using POT-sized sprite holders
     */
    @Inject(method = "stitch", at = @At("HEAD"), cancellable = true)
    public void stitch(CallbackInfo ci) {
        if (this.mipLevel == 0) {
            return;
        }

        List<Stitcher.Holder<T>> list = new ArrayList<>(this.texturesToBeStitched);
        list.sort(POT_HOLDER_COMPARATOR);

        long pixelCount = 0;
        for (Stitcher.Holder<T> holder : list) {
            var spriteSize = ((StitcherHolderExtension) (Object) holder).atlasSize();
            pixelCount += (long) spriteSize * spriteSize;
        }

        // throw if too big
        if (pixelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Too many pixels to stitch: " + pixelCount);
        }

        int pixelCountInt = (int) pixelCount;

        // smear the bits of the pixel count downwards
        pixelCountInt |= pixelCountInt >> 16;
        pixelCountInt |= pixelCountInt >> 8;
        pixelCountInt |= pixelCountInt >> 4;
        pixelCountInt |= pixelCountInt >> 2;
        pixelCountInt |= pixelCountInt >> 1;

        this.storageX = deinterleave(pixelCountInt) + 1;
        this.storageY = deinterleave(pixelCountInt >> 1) + 1;

        int nextFree = 0;
        for (Stitcher.Holder<T> holder : list) {
            // deinterleaving the sum of the pixels arranges the sprites in a z curve.
            // since the sprites are sorted by descending size, this never yields packing conflicts.
            var region = new Stitcher.Region<T>(
                    deinterleave(nextFree), deinterleave(nextFree >> 1),
                    holder.width(), holder.height());
            region.add(holder);
            this.storage.add(region);

            var spriteSize = ((StitcherHolderExtension) (Object) holder).atlasSize();
            nextFree += spriteSize * spriteSize;
        }

        ci.cancel();
    }

    @Unique
    private static int deinterleave(int n) {
        n = n & 0x55555555;
        n = (n | (n >> 1)) & 0x33333333;
        n = (n | (n >> 2)) & 0x0F0F0F0F;
        n = (n | (n >> 4)) & 0x00FF00FF;
        n = (n | (n >> 8)) & 0x0000FFFF;
        return n;
    }
}

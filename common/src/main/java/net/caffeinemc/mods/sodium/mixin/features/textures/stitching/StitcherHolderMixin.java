package net.caffeinemc.mods.sodium.mixin.features.textures.stitching;

import net.caffeinemc.mods.sodium.client.render.texture.StitcherHolderExtension;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.texture.Stitcher$Holder")
public class StitcherHolderMixin implements StitcherHolderExtension {
    private int atlasSize;

    // injection into the constructor of the Holder class
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/texture/Stitcher$Entry;II)V", at = @At("RETURN"))
    public void calculateAtlasSize(Stitcher.Entry entry, int width, int height, CallbackInfo ci) {
        this.atlasSize = Mth.smallestEncompassingPowerOfTwo(Math.max(width, height));
    }

    @Unique
    @Override
    public int atlasSize() {
        return this.atlasSize;
    }
}

package dev.kingcapture.mixin;

import dev.kingcapture.core.RecordingController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    /** After a full frame has been rendered into the main framebuffer. Cheap no-op when not recording. */
    @Inject(method = "render", at = @At("TAIL"))
    private void kingcapture$afterRender(boolean tick, CallbackInfo ci) {
        RecordingController.onFrameRendered();
    }
}

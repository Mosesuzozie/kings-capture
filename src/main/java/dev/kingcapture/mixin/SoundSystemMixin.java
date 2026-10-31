package dev.kingcapture.mixin;

import dev.kingcapture.audio.GameAudioMixer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes sound playback for the software mixer. require=0 everywhere: if a hook does not match this exact
 * Minecraft build, the mod still loads and records video (audio then stays silent; see README).
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    // 1.21.5+ returns SoundSystem.PlayResult. Verify the descriptor with `genSources` if audio stays silent.
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("RETURN"), require = 0)
    private void kingcapture$play(SoundInstance s, CallbackInfoReturnable<?> cir) { GameAudioMixer.onPlay(s); }

    @Inject(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), require = 0)
    private void kingcapture$stop(SoundInstance s, CallbackInfo ci) { GameAudioMixer.onStop(s); }

    @Inject(method = "stopAll()V", at = @At("HEAD"), require = 0)
    private void kingcapture$stopAll(CallbackInfo ci) { GameAudioMixer.onStopAll(); }

    @Inject(method = "pauseAll()V", at = @At("HEAD"), require = 0)
    private void kingcapture$pause(CallbackInfo ci) { GameAudioMixer.onPause(true); }

    @Inject(method = "resumeAll()V", at = @At("HEAD"), require = 0)
    private void kingcapture$resume(CallbackInfo ci) { GameAudioMixer.onPause(false); }
}

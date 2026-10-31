package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic pre-flight check. The numbers are rough estimates, not measurements: they exist to stop
 * obviously impossible settings and to warn about risky ones. Capture size is the real Minecraft
 * framebuffer, so anything larger than the game's render size is refused instead of upscaled.
 */
public final class CapabilityChecker {
    public record Plan(int w, int h, int fps, int videoKbps, List<String> warnings, List<String> blockers) { }

    public static Plan evaluate(KcConfig c, int srcW, int srcH, Path dir, boolean hardware) {
        List<String> warn = new ArrayList<>(), block = new ArrayList<>();
        int tw, th;
        if (c.resolution == KcConfig.Res.CUSTOM) { tw = c.customWidth; th = c.customHeight; }
        else { th = c.resolution.height; tw = Math.round(th * (float) srcW / srcH); }
        int fps = c.fps.value;

        double budget = Runtime.getRuntime().availableProcessors() * (hardware ? 30e6 : 6e6); // est. px/s
        if (tw > srcW || th > srcH) {
            block.add("Requested " + tw + "x" + th + " is larger than Minecraft's render size " + srcW + "x" + srcH
                    + ". KingCapture only records real rendered pixels (no upscaling, no off-screen supersampling). "
                    + "Raise the render resolution in PojavLauncher or choose " + suggest(srcW, srcH, budget) + ".");
            tw = Math.min(tw, srcW); th = Math.min(th, srcH);
        }
        tw &= ~1; th &= ~1;
        if (tw < 64 || th < 64) block.add("Resolution is too small (minimum 64x64).");

        long pxRate = (long) tw * th * fps;
        if (pxRate > budget * 2.5)
            block.add("Estimated encoding load (" + tw + "x" + th + " @ " + fps + " FPS) is far above what this device can sustain"
                    + (hardware ? "" : " with software encoding") + ". Try " + suggest(srcW, srcH, budget) + ".");
        else if (pxRate > budget)
            warn.add("This setting will likely cause FPS drops and dropped video frames. Try " + suggest(srcW, srcH, budget) + ".");
        else if (pxRate > budget * 0.6)
            warn.add("Sustained recording at this setting may heat the device and reduce FPS over time.");
        if (fps >= 120) warn.add("120 FPS only helps if Minecraft itself renders at 120+ FPS; otherwise frames are duplicated.");

        int kbps = c.bitrateKbps > 0 ? c.bitrateKbps : (int) Math.max(500, Math.min(80_000, pxRate * c.quality.bpp / 1000.0));
        if (kbps > 40_000) warn.add("Very high bitrate: about " + (kbps * 60L / 8 / 1000) + " MB per minute.");

        long perFrame = (long) srcW * srcH * 4;
        long heap = Runtime.getRuntime().maxMemory();
        if (heap < 1_500_000_000L) warn.add("Low Java heap (" + heap / 1_000_000 + " MB). Increase RAM in the launcher profile for stable recording.");
        if (c.memorySafeguard && perFrame * 2 > heap * 0.3)
            block.add("Not enough memory for frame buffers at " + srcW + "x" + srcH + ". Lower the render resolution or allocate more RAM.");

        long free = StoragePaths.freeBytes(dir);
        long perMin = (kbps + (c.audioEnabled ? c.audioKbps : 0)) * 60L * 125L; // bytes/min
        if (free >= 0) {
            if (free < 200_000_000L) block.add("Insufficient storage: only " + free / 1_000_000 + " MB free.");
            else if (free < perMin * 10) warn.add("Only about " + (free / Math.max(1, perMin)) + " minutes of space left at this bitrate.");
        }
        if (!c.warnings) warn.clear();
        return new Plan(tw, th, fps, kbps, warn, block);
    }

    static String suggest(int srcW, int srcH, double budget) {
        int[] hs = {2160, 1440, 1080, 720, 480};
        int[] fs = {60, 30, 24};
        for (int h : hs) {
            if (h > srcH) continue;
            int w = Math.round(h * (float) srcW / srcH);
            for (int f : fs) if ((long) w * h * f <= budget * 0.8) return h + "p @ " + f + " FPS";
        }
        return "480p @ 24 FPS";
    }
}

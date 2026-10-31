package dev.kingcapture.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings (config/kingcapture.json). Safe defaults so the mod works without Mod Menu. */
public final class KcConfig {
    public enum Res {
        P480(480, "480p"), P720(720, "720p"), P1080(1080, "1080p"), P1440(1440, "1440p"),
        P2160(2160, "2160p (4K)"), P4320(4320, "4320p (8K)"), CUSTOM(0, "Custom");
        public final int height; public final String label;
        Res(int h, String l) { height = h; label = l; }
    }
    public enum Fps { F24(24), F30(30), F60(60), F120(120); public final int value; Fps(int v) { value = v; } }
    /** bits per pixel per frame used when bitrate is "Auto". */
    public enum Quality {
        LOW(0.04, "Low"), BALANCED(0.07, "Balanced"), HIGH(0.11, "High"), MAXIMUM(0.18, "Maximum");
        public final double bpp; public final String label;
        Quality(double b, String l) { bpp = b; label = l; }
    }
    /** QUALITY = bigger frame queue + slower x264 preset; SMOOTH = tiny queue, fastest preset, drops frames first. */
    public enum Mode { QUALITY, BALANCED, SMOOTH }

    // Recording
    public Res resolution = Res.P720;
    public int customWidth = 1280, customHeight = 720;
    public Fps fps = Fps.F30;
    public Quality quality = Quality.BALANCED;
    public int bitrateKbps = 0;            // 0 = auto from quality preset
    public String encoder = "auto";        // auto | h264_mediacodec | libx264 | libopenh264
    // Audio
    public boolean audioEnabled = true;
    public int audioKbps = 128;
    public int audioOffsetMs = 0;          // + delays audio, - advances audio
    // Storage
    public String outputDir = "auto";
    public String filenameFormat = "KingCapture_{date}_{time}";
    public boolean autoTimestamp = true;
    // Performance
    public Mode mode = Mode.BALANCED;
    public boolean warnings = true;
    public boolean memorySafeguard = true;
    public boolean tryHardware = true;
    // Interface
    public boolean showIndicator = true;
    public boolean showTimer = true;
    public boolean showEscButton = true;
    public boolean showRecordingsButton = true;
    public boolean statusMessages = true;

    public enum Preset { LOW, BALANCED, HIGH, MAXIMUM }

    public void applyPreset(Preset p) {
        switch (p) {
            case LOW -> { resolution = Res.P480; fps = Fps.F30; quality = Quality.LOW; mode = Mode.SMOOTH; audioKbps = 96; }
            case BALANCED -> { resolution = Res.P720; fps = Fps.F30; quality = Quality.BALANCED; mode = Mode.BALANCED; audioKbps = 128; }
            case HIGH -> { resolution = Res.P1080; fps = Fps.F30; quality = Quality.HIGH; mode = Mode.BALANCED; audioKbps = 192; }
            case MAXIMUM -> { resolution = Res.P1080; fps = Fps.F60; quality = Quality.MAXIMUM; mode = Mode.QUALITY; audioKbps = 256; }
        }
        bitrateKbps = 0;
    }

    // ---- persistence ----
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KcConfig inst = new KcConfig();
    public static KcConfig get() { return inst; }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("kingcapture.json"); }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                KcConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), KcConfig.class);
                if (c != null) inst = c;
            }
        } catch (Exception e) { System.err.println("[KingCapture] config load failed, using defaults: " + e); inst = new KcConfig(); }
        inst.sanitize();
        save();
    }

    public static synchronized void save() {
        try {
            inst.sanitize();
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(inst), StandardCharsets.UTF_8);
        } catch (Exception e) { System.err.println("[KingCapture] config save failed: " + e); }
    }

    private void sanitize() {
        if (resolution == null) resolution = Res.P720;
        if (fps == null) fps = Fps.F30;
        if (quality == null) quality = Quality.BALANCED;
        if (mode == null) mode = Mode.BALANCED;
        if (encoder == null || encoder.isBlank()) encoder = "auto";
        if (outputDir == null || outputDir.isBlank()) outputDir = "auto";
        if (filenameFormat == null || filenameFormat.isBlank()) filenameFormat = "KingCapture_{date}_{time}";
        customWidth = Math.max(64, Math.min(16384, customWidth));
        customHeight = Math.max(64, Math.min(16384, customHeight));
        audioKbps = Math.max(32, Math.min(320, audioKbps));
        audioOffsetMs = Math.max(-1000, Math.min(1000, audioOffsetMs));
        bitrateKbps = Math.max(0, Math.min(200_000, bitrateKbps));
    }

    public KcConfig copy() { return GSON.fromJson(GSON.toJson(this), KcConfig.class); }

    public static <T extends Enum<T>> T cycle(T v) {
        T[] a = v.getDeclaringClass().getEnumConstants();
        return a[(v.ordinal() + 1) % a.length];
    }
}

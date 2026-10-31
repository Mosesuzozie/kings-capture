package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks the first H.264 encoder that really opens. Hardware (MediaCodec) is tried first, but the
 * bundled FFmpeg's MediaCodec wrapper needs an ART JavaVM, which a Pojav JVM usually lacks, so expect
 * it to fail and software x264 to be used. A lock file guards against native crashes during that probe:
 * if the game died while probing, hardware encoding is permanently disabled (delete hw_disabled to retry).
 */
public final class EncoderProbe {
    private static final Map<String, Boolean> RESULT = new ConcurrentHashMap<>();
    private static volatile String log = "";

    public static boolean isHardware(String n) {
        return n != null && (n.contains("mediacodec") || n.contains("v4l2") || n.contains("nvenc") || n.contains("vaapi") || n.contains("videotoolbox"));
    }
    public static String log() { return log; }

    public static synchronized String select(KcConfig c) {
        List<String> order = new ArrayList<>();
        if (!"auto".equals(c.encoder)) order.add(c.encoder);
        else {
            if (c.tryHardware) order.add("h264_mediacodec");
            order.add("libx264");
            order.add("libopenh264");
        }
        StringBuilder sb = new StringBuilder();
        for (String name : order) {
            boolean ok = works(name);
            sb.append(name).append(ok ? ": OK; " : ": unavailable; ");
            if (ok) { log = sb.toString(); return name; }
        }
        log = sb.toString();
        return null;
    }

    private static boolean works(String name) {
        Boolean cached = RESULT.get(name);
        if (cached != null) return cached;
        boolean ok;
        try {
            if (avcodec.avcodec_find_encoder_by_name(name) == null) ok = false;
            else if (isHardware(name)) ok = guarded(name);
            else ok = test(name);
        } catch (Throwable t) { ok = false; }
        RESULT.put(name, ok);
        return ok;
    }

    private static boolean guarded(String name) throws Exception {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("kingcapture");
        Files.createDirectories(dir);
        Path lock = dir.resolve("hw_probe.lock"), disabled = dir.resolve("hw_disabled");
        if (Files.exists(lock)) { Files.writeString(disabled, "A previous hardware encoder probe crashed the game."); Files.deleteIfExists(lock); }
        if (Files.exists(disabled)) return false;
        Files.writeString(lock, name);
        try { return test(name); } finally { Files.deleteIfExists(lock); }
    }

    private static boolean test(String name) {
        File tmp = null;
        try {
            tmp = Files.createTempFile(FabricLoader.getInstance().getGameDir(), "kc_probe_", ".mp4").toFile();
            FFmpegFrameRecorder r = FfmpegFactory.create(tmp, 640, 360, 30, 2000, name, KcConfig.get(), false);
            r.start();
            ByteBuffer bb = ByteBuffer.allocateDirect(640 * 360 * 4).order(ByteOrder.LITTLE_ENDIAN);
            r.recordImage(640, 360, 8, 4, 640 * 4, avutil.AV_PIX_FMT_BGRA, bb);
            r.stop(); r.release();
            return true;
        } catch (Throwable t) {
            return false;
        } finally { if (tmp != null) tmp.delete(); }
    }
}

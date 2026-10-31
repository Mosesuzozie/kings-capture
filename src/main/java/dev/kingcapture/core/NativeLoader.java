package dev.kingcapture.core;

import net.fabricmc.loader.api.FabricLoader;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.javacpp.Loader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the bundled FFmpeg natives lazily (never at game start) and never throws.
 * On PojavLauncher the JVM reports os.name=Linux / arch=aarch64, so JavaCPP would look for glibc
 * "linux-arm64" libraries that cannot load on Android's bionic libc. We force "android-arm64".
 */
public final class NativeLoader {
    private static boolean tried;
    private static volatile boolean ready;
    private static volatile String status = "Not tested yet";

    public static boolean looksLikeAndroid() {
        return System.getenv("POJAV_RENDERER") != null
                || System.getenv("POJAV_NATIVEDIR") != null
                || String.valueOf(System.getProperty("java.vm.vendor")).toLowerCase().contains("android")
                || Files.exists(Path.of("/system/build.prop"));
    }

    public static synchronized boolean ensure() {
        if (tried) return ready;
        tried = true;
        try {
            Path cache = FabricLoader.getInstance().getGameDir().resolve("kingcapture").resolve("natives");
            Files.createDirectories(cache);
            if (System.getProperty("org.bytedeco.javacpp.cachedir") == null)
                System.setProperty("org.bytedeco.javacpp.cachedir", cache.toString());
            boolean android = looksLikeAndroid();
            if (android && System.getProperty("org.bytedeco.javacpp.platform") == null)
                System.setProperty("org.bytedeco.javacpp.platform", "android-arm64");
            Loader.load(avutil.class);
            Loader.load(swresample.class);
            Loader.load(swscale.class);
            Loader.load(avcodec.class);
            Loader.load(avformat.class);
            avutil.av_log_set_level(avutil.AV_LOG_ERROR);
            ready = true;
            status = "OK (platform " + Loader.Detector.getPlatform() + ", " + (android ? "Android/Pojav detected" : "desktop") + ")";
        } catch (Throwable t) {
            ready = false;
            status = "FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            System.err.println("[KingCapture] FFmpeg native load failed: " + t);
        }
        return ready;
    }

    public static boolean isReady() { return ready; }
    public static String status() { return status; }
}

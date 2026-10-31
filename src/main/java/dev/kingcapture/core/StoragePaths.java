package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Plain java.nio paths only: Android's SAF/MediaStore are not reachable from a Pojav JVM. */
public final class StoragePaths {
    public static Path resolveOutputDir(KcConfig c) throws IOException {
        if (!"auto".equals(c.outputDir)) {
            Path p = Path.of(c.outputDir);
            Files.createDirectories(p);
            if (!Files.isWritable(p)) throw new IOException("Output folder is not writable: " + p);
            return p;
        }
        Path[] candidates = {
                Path.of("/storage/emulated/0/Movies/KingCapture"),
                Path.of("/sdcard/Movies/KingCapture"),
                FabricLoader.getInstance().getGameDir().resolve("kingcapture").resolve("recordings")
        };
        for (Path p : candidates) {
            try { Files.createDirectories(p); if (Files.isWritable(p)) return p; } catch (Exception ignored) { }
        }
        throw new IOException("No writable output folder found. Choose one in KingCapture > Storage.");
    }

    public static long freeBytes(Path dir) {
        try { return Files.getFileStore(dir).getUsableSpace(); } catch (Exception e) { return -1; }
    }

    public static Path uniqueFile(Path dir, KcConfig c) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String time = now.format(DateTimeFormatter.ofPattern("HH-mm-ss"));
        String name = c.filenameFormat;
        boolean hasToken = name.contains("{date}") || name.contains("{time}");
        name = name.replace("{date}", date).replace("{time}", time);
        if (c.autoTimestamp && !hasToken) name += "_" + date + "_" + time;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path p = dir.resolve(name + ".mp4");
        for (int i = 1; Files.exists(p); i++) p = dir.resolve(name + "_" + i + ".mp4");
        return p;
    }
}

package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Local MP4 library. It has no ReplayMod dependency or replay-file integration. */
public final class RecordingFiles {
    private RecordingFiles() { }

    public static List<Path> list() throws IOException {
        Path folder = StoragePaths.resolveOutputDir(KcConfig.get());
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4"))
                    .sorted(Comparator.comparingLong(RecordingFiles::modifiedMillis).reversed())
                    .toList();
        }
    }

    public static long size(Path file) {
        try { return Files.size(file); } catch (IOException ignored) { return 0; }
    }

    public static Path copyToDownloads(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("The recording no longer exists.");
        Path folder = downloadsDirectory();
        Files.createDirectories(folder);
        if (!Files.isWritable(folder)) throw new IOException("Downloads folder is not writable: " + folder);

        String name = source.getFileName().toString();
        Path target = folder.resolve(name);
        String base = name.toLowerCase(Locale.ROOT).endsWith(".mp4") ? name.substring(0, name.length() - 4) : name;
        for (int n = 1; Files.exists(target); n++) target = folder.resolve(base + "_" + n + ".mp4");
        return Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static Path downloadsDirectory() throws IOException {
        Path[] candidates = {
                Path.of("/storage/emulated/0/Download/KingCapture"),
                Path.of("/sdcard/Download/KingCapture"),
                FabricLoader.getInstance().getGameDir().resolve("kingcapture").resolve("downloads")
        };
        IOException last = null;
        for (Path candidate : candidates) {
            try {
                Files.createDirectories(candidate);
                if (Files.isDirectory(candidate) && Files.isWritable(candidate)) return candidate;
            } catch (IOException ex) { last = ex; }
        }
        throw new IOException("No writable Downloads folder is available.", last);
    }

    /** Opens the MP4 in the device's normal video player, retaining its recorded audio. */
    public static void watch(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("The recording no longer exists.");
        String absolutePath = file.toAbsolutePath().toString();

        // PojavLauncher exposes this kept Android bridge. Use reflection so desktop Fabric builds
        // do not need Android classes or a launcher dependency on their compile classpath.
        try {
            Class<?> mainActivity = Class.forName("net.kdt.pojavlaunch.MainActivity");
            Method openLink = mainActivity.getMethod("openLink", String.class);
            openLink.invoke(null, "file:" + absolutePath);
            return;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Not running under the classic PojavLauncher (or its bridge is unavailable).
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (Throwable ex) {
            throw new IOException("Could not open a video player: " + ex.getMessage(), ex);
        }
        throw new IOException("No supported video-player bridge was found. Open this file manually: " + absolutePath);
    }

    private static long modifiedMillis(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0; }
    }
}

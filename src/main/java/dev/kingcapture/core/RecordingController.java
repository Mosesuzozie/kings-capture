package dev.kingcapture.core;

import dev.kingcapture.audio.GameAudioMixer;
import dev.kingcapture.config.KcConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** The single shared controller used by the ESC button, the keybind and the HUD. */
public final class RecordingController {
    public enum State { IDLE, STARTING, RECORDING, STOPPING }

    private static volatile State state = State.IDLE;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KingCapture-Worker"); t.setDaemon(true); return t;
    });
    private static volatile RecorderSession session;
    private static volatile GameAudioMixer mixer;
    private static volatile long startNanos;
    private static volatile int fps;
    private static long nextIdx;
    private static final AtomicInteger inFlight = new AtomicInteger();

    public static State state() { return state; }
    public static boolean isRecording() { return state == State.RECORDING; }

    public static Text buttonLabel() {
        return switch (state) {
            case IDLE -> Text.literal("\u25B6 Start Recording");
            case RECORDING -> Text.literal("\u25A0 Stop Recording");
            case STARTING -> Text.literal("Starting\u2026");
            case STOPPING -> Text.literal("Saving\u2026");
        };
    }

    public static long elapsedMillis() { return state == State.RECORDING ? (System.nanoTime() - startNanos) / 1_000_000L : 0; }
    public static long fileBytes() { RecorderSession s = session; return s == null ? 0 : s.bytes(); }
    public static String fmtTime(long ms) { long s = ms / 1000; return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60); }

    /** Only IDLE and RECORDING react; transitional states ignore input, so two sessions can never overlap. */
    public static synchronized void toggle() {
        if (state == State.IDLE) start();
        else if (state == State.RECORDING) stop();
    }

    // ---------------- start ----------------
    private static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getFramebufferWidth(), sh = mc.getWindow().getFramebufferHeight();
        KcConfig cfg = KcConfig.get().copy();
        state = State.STARTING;
        WORKER.submit(() -> doStart(cfg, sw, sh));
    }

    private static void doStart(KcConfig cfg, int sw, int sh) {
        RecorderSession s = null;
        try {
            if (!NativeLoader.ensure())
                throw new Exception("FFmpeg natives could not be loaded on this device (" + NativeLoader.status() + "). Recording is unavailable.");
            Path dir = StoragePaths.resolveOutputDir(cfg);
            String encoder = EncoderProbe.select(cfg);
            if (encoder == null) throw new Exception("No usable H.264 encoder in the bundled FFmpeg (" + EncoderProbe.log() + ").");
            CapabilityChecker.Plan plan = CapabilityChecker.evaluate(cfg, sw, sh, dir, EncoderProbe.isHardware(encoder));
            if (!plan.blockers().isEmpty()) throw new Exception(String.join(" ", plan.blockers()));
            if (cfg.statusMessages || !plan.warnings().isEmpty())
                for (String w : plan.warnings()) toast("KingCapture warning", w, true);

            long perFrame = (long) sw * sh * 4;
            Runtime rt = Runtime.getRuntime();
            long avail = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            int modeQ = switch (cfg.mode) { case QUALITY -> 6; case BALANCED -> 4; case SMOOTH -> 2; };
            int queue = cfg.memorySafeguard ? (int) Math.max(1, Math.min(modeQ, avail / 4 / perFrame)) : modeQ;

            Path file = StoragePaths.uniqueFile(dir, cfg);
            s = new RecorderSession(file, plan.w(), plan.h(), plan.fps(), plan.videoKbps(), encoder, cfg, queue,
                    msg -> onRuntimeFailure(msg));
            s.start();

            GameAudioMixer m = null;
            if (cfg.audioEnabled) m = new GameAudioMixer(s, cfg.audioOffsetMs);

            session = s; mixer = m;
            fps = plan.fps(); nextIdx = 0; inFlight.set(0);
            startNanos = System.nanoTime();
            if (m != null) m.start(startNanos);
            state = State.RECORDING;
            toast("KingCapture", "Recording " + plan.w() + "x" + plan.h() + " @ " + plan.fps() + " FPS (" + encoder
                    + (cfg.audioEnabled ? ", game audio" : ", no audio") + ")", false);
        } catch (Throwable t) {
            if (s != null) s.abort();
            session = null; mixer = null;
            state = State.IDLE;
            toast("KingCapture: cannot record", String.valueOf(t.getMessage()), true);
        }
    }

    // ---------------- per-frame hook (render thread) ----------------
    public static void onFrameRendered() {
        if (state != State.RECORDING) return;
        RecorderSession s = session;
        if (s == null || inFlight.get() >= 2) return;
        long idx = (System.nanoTime() - startNanos) * fps / 1_000_000_000L;
        if (idx < nextIdx) return;
        nextIdx = idx + 1;
        inFlight.incrementAndGet();
        try {
            ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer(), img -> {
                try {
                    s.offerFrame(img.copyPixelsArgb(), img.getWidth(), img.getHeight(), idx);
                } catch (Throwable t) {
                    onRuntimeFailure("Frame capture failed: " + t);
                } finally {
                    img.close();
                    inFlight.decrementAndGet();
                }
            });
        } catch (Throwable t) {
            inFlight.decrementAndGet();
            onRuntimeFailure("Frame capture failed: " + t);
        }
    }

    // ---------------- stop ----------------
    private static void stop() {
        state = State.STOPPING;
        WORKER.submit(RecordingController::doStop);
    }

    private static void doStop() {
        RecorderSession s = session;
        GameAudioMixer m = mixer;
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        try {
            if (m != null) m.stop();
            if (s != null) {
                s.finish();
                long mb = s.bytes() / 1_000_000L;
                toast("KingCapture: saved", s.file().getFileName() + "  (" + fmtTime(ms) + ", " + mb + " MB"
                        + (s.dropped() > 0 ? ", " + s.dropped() + " dropped frames" : "") + ")\n" + s.file().getParent(), false);
            }
        } catch (Throwable t) {
            toast("KingCapture: save problem", String.valueOf(t.getMessage())
                    + (s != null ? " Partial file (fragmented MP4, usually still playable): " + s.file() : ""), true);
        } finally {
            session = null; mixer = null;
            state = State.IDLE;
        }
    }

    private static synchronized void onRuntimeFailure(String msg) {
        if (state == State.RECORDING) {
            toast("KingCapture: recording error", msg + " Stopping and saving what was recorded.", true);
            stop();
        }
    }

    /** Called when the game closes. Waits briefly so the MP4 gets finalized. */
    public static void shutdownBlocking() {
        if (state == State.RECORDING) {
            stop();
            try { WORKER.shutdown(); WORKER.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        }
    }

    /** Self-test used by the settings screen: loads natives and probes encoders without recording. */
    public static void runSelfTest() {
        WORKER.submit(() -> {
            boolean ok = NativeLoader.ensure();
            String enc = ok ? EncoderProbe.select(KcConfig.get()) : null;
            toast("KingCapture self-test",
                    "Natives: " + NativeLoader.status() + "\nEncoder: " + (enc == null ? "none (" + EncoderProbe.log() + ")" : enc + (EncoderProbe.isHardware(enc) ? " (hardware)" : " (software)")), !ok || enc == null);
        });
    }

    public static void toast(String title, String msg, boolean always) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!always && !KcConfig.get().statusMessages) return;
        mc.execute(() -> SystemToast.add(mc.getToastManager(), new SystemToast.Type(), Text.literal(title), Text.literal(msg)));
        System.out.println("[KingCapture] " + title + ": " + msg);
    }
}

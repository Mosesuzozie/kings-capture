package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One recording: a single writer thread owns the FFmpeg recorder (it is not thread-safe) and drains a queue of
 * video frames (full-size ARGB from the framebuffer; swscale scales/converts to YUV420 natively) and
 * 20 ms stereo PCM audio chunks. Video is constant-frame-rate: gaps are filled by repeating the last frame.
 */
public final class RecorderSession {
    private record Video(int[] argb, int w, int h, long idx) { }
    private static final Object POISON = new Object();

    private final Path file;
    private final int outW, outH, fps, maxVideoQueue;
    private final FFmpegFrameRecorder rec;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger videoQueued = new AtomicInteger();
    private final boolean audio;
    private final Consumer<String> onFailure;
    private volatile boolean closed;
    private volatile Throwable failure;
    private volatile long framesWritten, dropped;
    private Thread thread;

    public RecorderSession(Path file, int outW, int outH, int fps, int kbps, String encoder, KcConfig cfg,
                           int maxVideoQueue, Consumer<String> onFailure) {
        this.file = file; this.outW = outW; this.outH = outH; this.fps = fps;
        this.maxVideoQueue = Math.max(1, maxVideoQueue);
        this.audio = cfg.audioEnabled;
        this.onFailure = onFailure;
        this.rec = FfmpegFactory.create(file.toFile(), outW, outH, fps, kbps, encoder, cfg, audio);
    }

    public void start() throws Exception {
        rec.start();
        thread = new Thread(this::loop, "KingCapture-Encoder");
        thread.setDaemon(true);
        thread.start();
    }

    /** Called on the render thread. Never blocks. */
    public boolean offerFrame(int[] argb, int w, int h, long idx) {
        if (closed || failure != null) return false;
        if (videoQueued.get() >= maxVideoQueue) { dropped++; return false; }
        videoQueued.incrementAndGet();
        queue.add(new Video(argb, w, h, idx));
        return true;
    }

    public void offerAudio(short[] interleavedStereo) {
        if (!closed && failure == null) queue.add(interleavedStereo);
    }

    private void loop() {
        long last = -1;
        ByteBuffer buf = null;
        int bw = 0, bh = 0;
        try {
            while (true) {
                Object o = queue.take();
                if (o == POISON) break;
                if (failure != null) continue; // keep draining so memory is released
                try {
                    if (o instanceof Video v) {
                        videoQueued.decrementAndGet();
                        if (v.idx <= last) continue;
                        if (buf == null || bw != v.w || bh != v.h) {
                            buf = ByteBuffer.allocateDirect(v.w * v.h * 4).order(ByteOrder.LITTLE_ENDIAN);
                            bw = v.w; bh = v.h;
                        }
                        buf.clear();
                        buf.asIntBuffer().put(v.argb);   // ARGB int, little-endian bytes = B,G,R,A = AV_PIX_FMT_BGRA
                        long from = Math.max(last + 1, v.idx - fps);   // fill gaps by repeating, at most 1 s
                        for (long i = from; i <= v.idx; i++) {
                            buf.position(0);
                            rec.setTimestamp(i * 1_000_000L / fps);
                            rec.recordImage(v.w, v.h, 8, 4, v.w * 4, avutil.AV_PIX_FMT_BGRA, buf);
                            framesWritten++;
                        }
                        last = v.idx;
                    } else if (o instanceof short[] a && audio) {
                        rec.recordSamples(FfmpegFactory.SAMPLE_RATE, 2, ShortBuffer.wrap(a));
                    }
                } catch (Throwable t) {
                    failure = t;
                    if (onFailure != null) onFailure.accept(String.valueOf(t.getMessage()));
                }
            }
        } catch (InterruptedException ignored) { }
    }

    /** Flush + write trailer. Blocking: call from the worker thread, never the render thread. */
    public void finish() throws Exception {
        closed = true;
        queue.add(POISON);
        if (thread != null) thread.join(60_000);
        try { rec.stop(); } finally { rec.release(); }
        if (failure != null) throw new Exception("Recording failed: " + failure.getMessage(), failure);
    }

    /** Failed start / error path: release resources and delete an empty file. */
    public void abort() {
        closed = true;
        queue.add(POISON);
        try { rec.release(); } catch (Throwable ignored) { }
        try { if (Files.exists(file) && Files.size(file) < 1024) Files.delete(file); } catch (Exception ignored) { }
    }

    public Path file() { return file; }
    public long bytes() { try { return Files.size(file); } catch (Exception e) { return 0; } }
    public long framesWritten() { return framesWritten; }
    public long dropped() { return dropped; }
    public int outW() { return outW; }
    public int outH() { return outH; }
    public int fps() { return fps; }
}

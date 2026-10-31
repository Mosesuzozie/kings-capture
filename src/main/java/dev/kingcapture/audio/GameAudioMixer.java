package dev.kingcapture.audio;

import dev.kingcapture.core.RecorderSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Game audio capture WITHOUT touching the OpenAL output device.
 *
 * Why: OpenAL Soft cannot be tapped from outside once Minecraft owns the device, and the desktop tricks
 * (loopback devices, system "what you hear" capture) are unavailable in Pojav. Instead we observe every
 * SoundInstance Minecraft plays (SoundSystemMixin), decode the same .ogg resources with Minecraft's own
 * OggAudioStream and mix them in software at 44.1 kHz stereo, applying volume sliders, pitch, linear
 * distance attenuation and left/right panning. The mix is clocked against the same nanoTime origin as the
 * video, so it stays in sync. It is an faithful approximation, not a bit-exact copy of what OpenAL outputs
 * (no reverb/doppler/occlusion; Mojang's exact rolloff curve is approximated).
 */
public final class GameAudioMixer {
    private static final int RATE = 44100, CHUNK = 882; // 20 ms
    private static volatile GameAudioMixer active;
    public static volatile long eventsSeen;

    private final RecorderSession session;
    private final int offsetMs;
    private final List<Voice> voices = new CopyOnWriteArrayList<>();
    private volatile boolean running, paused;
    private Thread thread;

    public GameAudioMixer(RecorderSession session, int offsetMs) { this.session = session; this.offsetMs = offsetMs; }

    // ---- hooks called from SoundSystemMixin (render/main thread) ----
    public static void onPlay(SoundInstance i) { GameAudioMixer m = active; eventsSeen++; if (m != null) m.add(i); }
    public static void onStop(SoundInstance i) { GameAudioMixer m = active; if (m != null) for (Voice v : m.voices) if (v.inst == i) v.stopped = true; }
    public static void onStopAll() { GameAudioMixer m = active; if (m != null) for (Voice v : m.voices) v.stopped = true; }
    public static void onPause(boolean p) { GameAudioMixer m = active; if (m != null) m.paused = p; }

    private void add(SoundInstance i) {
        Sound s = i.getSound();
        if (s == null) return;
        Identifier id = s.getLocation();
        if (!id.getPath().endsWith(".ogg")) id = Identifier.of(id.getNamespace(), "sounds/" + id.getPath() + ".ogg");
        voices.add(new Voice(i, id));
    }

    public void start(long startNanos) {
        running = true; active = this;
        thread = new Thread(() -> run(startNanos), "KingCapture-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (active == this) active = null;
        try { if (thread != null) thread.join(3000); } catch (InterruptedException ignored) { }
        for (Voice v : voices) v.close();
        voices.clear();
    }

    private void run(long startNanos) {
        long produced = 0;
        long skip = offsetMs < 0 ? (long) (-offsetMs) * RATE / 1000 : 0;
        if (offsetMs > 0) { // delay audio: lead with silence
            long frames = (long) offsetMs * RATE / 1000;
            while (frames > 0) { int n = (int) Math.min(CHUNK, frames); session.offerAudio(new short[n * 2]); frames -= n; }
        }
        float[] l = new float[CHUNK], r = new float[CHUNK];
        while (running) {
            long target = (System.nanoTime() - startNanos) * RATE / 1_000_000_000L;
            while (running && produced + CHUNK <= target) {
                short[] pcm = mix(l, r);
                produced += CHUNK;
                if (skip >= CHUNK) { skip -= CHUNK; continue; }
                session.offerAudio(pcm);
            }
            try { Thread.sleep(4); } catch (InterruptedException e) { return; }
        }
    }

    private short[] mix(float[] l, float[] r) {
        java.util.Arrays.fill(l, 0); java.util.Arrays.fill(r, 0);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!paused) {
            Vec3d lp = null; float yaw = 0;
            try { if (mc.player != null) { lp = mc.player.getEyePos(); yaw = mc.player.getYaw(); } } catch (Throwable ignored) { }
            for (Voice v : voices) {
                try { v.mix(l, r, CHUNK, lp, yaw, mc); } catch (Throwable t) { v.done = true; }
            }
            voices.removeIf(v -> { if (v.done || v.stopped || v.failed) { v.close(); return true; } return false; });
        }
        short[] out = new short[CHUNK * 2];
        for (int i = 0; i < CHUNK; i++) {
            out[2 * i] = clip(l[i]);
            out[2 * i + 1] = clip(r[i]);
        }
        return out;
    }

    private static short clip(float v) {
        if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
        return (short) (v * 32767f);
    }

    // =====================================================================================
    private static final class Voice {
        final SoundInstance inst; final Identifier res;
        AudioStream stream; int ch = 2; float rate = 44100;
        float[] d = new float[16384]; int count; double pos;
        volatile boolean stopped; boolean ended, failed, done;

        Voice(SoundInstance i, Identifier r) { inst = i; res = r; }

        boolean open(MinecraftClient mc) {
            try {
                var o = mc.getResourceManager().getResource(res);
                if (o.isEmpty()) { failed = true; return false; }
                InputStream in = o.get().getInputStream();
                stream = new OggAudioStream(in);
                AudioFormat f = stream.getFormat();
                ch = Math.max(1, f.getChannels());
                rate = f.getSampleRate();
                return true;
            } catch (Throwable t) { failed = true; return false; }
        }

        void close() { try { if (stream != null) stream.close(); } catch (Throwable ignored) { } stream = null; }

        private void pull(MinecraftClient mc) throws Exception {
            ByteBuffer b = stream.getBuffer(16384);
            if (b == null || !b.hasRemaining()) {
                if (inst.isRepeatable() && !stopped) { close(); if (!open(mc)) ended = true; }
                else ended = true;
                return;
            }
            ShortBuffer sb = b.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int frames = sb.remaining() / ch;
            int drop = (int) pos;
            if (drop > 0) { System.arraycopy(d, drop * ch, d, 0, (count - drop) * ch); count -= drop; pos -= drop; }
            if ((count + frames) * ch > d.length) d = java.util.Arrays.copyOf(d, Math.max(d.length * 2, (count + frames) * ch));
            for (int i = 0; i < frames * ch; i++) d[count * ch + i] = sb.get() / 32768f;
            count += frames;
        }

        void mix(float[] l, float[] r, int n, Vec3d lp, float yaw, MinecraftClient mc) throws Exception {
            if (stream == null && !open(mc)) return;
            // gains (re-evaluated every chunk so moving/fading sounds follow)
            float vol = Math.max(0f, inst.getVolume());
            var opt = mc.options;
            float g = Math.min(vol, 1f) * opt.getSoundVolume(SoundCategory.MASTER);
            if (inst.getCategory() != SoundCategory.MASTER) g *= opt.getSoundVolume(inst.getCategory());
            boolean positional = lp != null && !inst.isRelative() && inst.getAttenuationType() == SoundInstance.AttenuationType.LINEAR;
            float gl = g, gr = g;
            if (positional) {
                double dx = inst.getX() - lp.x, dy = inst.getY() - lp.y, dz = inst.getZ() - lp.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double range = Math.max(vol, 1f) * 16.0;
                float att = (float) Math.max(0.0, 1.0 - dist / range);
                double hl = Math.hypot(dx, dz), pan = 0;
                if (hl > 0.01) { double yr = Math.toRadians(yaw); pan = (dx * -Math.cos(yr) + dz * -Math.sin(yr)) / hl; }
                double a = (pan + 1) * Math.PI / 4;
                gl = g * att * (float) (Math.cos(a) * Math.sqrt(2)); gr = g * att * (float) (Math.sin(a) * Math.sqrt(2));
            }
            double ratio = rate * Math.max(0.1f, inst.getPitch()) / RATE;
            int need = (int) Math.ceil(n * ratio) + 2;
            int guard = 0;
            while (!ended && count - pos < need && guard++ < 64) pull(mc);
            for (int i = 0; i < n; i++) {
                int i0 = (int) pos;
                if (i0 + 1 >= count) { if (ended) done = true; break; }
                float f = (float) (pos - i0);
                float s0 = d[i0 * ch] * (1 - f) + d[(i0 + 1) * ch] * f;
                float s1 = ch > 1 ? d[i0 * ch + 1] * (1 - f) + d[(i0 + 1) * ch + 1] * f : s0;
                if (positional) { float m = (s0 + s1) * 0.5f; l[i] += m * gl; r[i] += m * gr; }
                else { l[i] += s0 * gl; r[i] += s1 * gr; }
                pos += ratio;
            }
        }
    }
}

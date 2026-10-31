package dev.kingcapture.core;

import dev.kingcapture.config.KcConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.io.File;

final class FfmpegFactory {
    static final int SAMPLE_RATE = 44100;

    /** Fragmented MP4 (frag_keyframe+empty_moov): a crash or kill leaves a playable file up to the last fragment. */
    static FFmpegFrameRecorder create(File out, int w, int h, int fps, int videoKbps,
                                      String encoder, KcConfig cfg, boolean audio) {
        FFmpegFrameRecorder r = new FFmpegFrameRecorder(out, w, h, audio ? 2 : 0);
        r.setFormat("mp4");
        r.setVideoCodecName(encoder);
        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        r.setFrameRate(fps);
        r.setVideoBitrate(videoKbps * 1000);
        r.setGopSize(fps * 2);
        if (encoder.equals("libx264")) {
            String preset = switch (cfg.mode) { case QUALITY -> "veryfast"; case BALANCED -> "superfast"; case SMOOTH -> "ultrafast"; };
            r.setVideoOption("preset", preset);
            r.setVideoOption("tune", "zerolatency");
        }
        if (audio) {
            r.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            r.setSampleRate(SAMPLE_RATE);
            r.setAudioChannels(2);
            r.setAudioBitrate(cfg.audioKbps * 1000);
        }
        r.setOption("movflags", "frag_keyframe+empty_moov+default_base_moof");
        return r;
    }
}

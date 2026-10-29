# KingCapture — in-game MP4 recorder (Fabric 1.21.11, PojavLauncher/Android ARM64)

**Status: source project, NOT compiled or run.** This workspace cannot download Gradle or Maven dependencies, so no installable JAR has been produced and nothing has been tested on a device. The ZIP includes a GitHub Actions workflow that attempts a real build; see [`BUILD_ON_GITHUB.md`](BUILD_ON_GITHUB.md). Expect possible compile/API fixes (Minecraft 1.21.11 risk points are listed below).

## Build

- **Automated:** Upload the contents of this project folder (including `.github/workflows/build.yml`) to a GitHub repository. The workflow builds with Java 21 / Gradle 9.2 and uploads the JAR as an Actions artifact only if the build succeeds. Read `BUILD_ON_GITHUB.md` for exact steps.
- **Local:** Install JDK 21 and Gradle 9.2+, then run `gradle clean build`. The expected output is `build/libs/kingcapture-1.0.0.jar` if compilation succeeds.

## Install on Android
Put the JAR + Fabric API (+ optional Mod Menu) in the profile's `mods` folder in PojavLauncher (Fabric Loader 1.21.11, Java 21 runtime). Give the profile ≥2 GB RAM.

## Use
* ESC menu: top-left button **▶ Start Recording / ■ Stop Recording** (shows Starting… / Saving… while busy).
* Keybind **KingCapture: Toggle Recording** — unbound by default; set it in Controls.
* Settings: Mod Menu → KingCapture (Recording, Audio, Storage, Performance, Interface). Without Mod Menu, defaults apply (720p, 30 FPS, Balanced, game audio on).
* Output: `/storage/emulated/0/Movies/KingCapture` if writable, else `<game dir>/kingcapture/recordings`.
* Run **Performance → Run self-test** first: it reports whether FFmpeg natives loaded in Pojav and which encoder was picked.

## How it works
| Part | Approach |
|---|---|
| Video | `ScreenshotRecorder.takeScreenshot(mainFramebuffer, cb)` after each rendered frame (async GPU readback, max 2 in flight) → queue → writer thread → FFmpeg (swscale scales to target, x264/MediaCodec encodes) |
| Audio | Software mix of every sound Minecraft plays (hook on `SoundSystem`), decoded from the game's own .ogg resources via `OggAudioStream`, mixed at 44.1 kHz stereo with volume sliders, pitch, distance attenuation and panning; clocked on the same `nanoTime` origin as video; AAC |
| Mux | FFmpeg MP4, H.264 + AAC, **fragmented** (`frag_keyframe+empty_moov`) so a crash/kill leaves a playable file |
| Natives | JavaCV/JavaCPP FFmpeg, **android-arm64 only**, loaded lazily; `org.bytedeco.javacpp.platform=android-arm64` is forced when Pojav/Android is detected; any load failure disables recording with a message instead of crashing |
| Encoder | Tries `h264_mediacodec` first (guarded by a crash-lock file), then `libx264`, then `libopenh264` |
| Threading | Render thread only copies pixels; encoding, muxing, file finalization, probing run on worker threads |

## Honest limitations
* **Resolution is capped at the game's render size.** 1440p/4K/8K are selectable but refused (with an explanation and a suggested setting) unless Minecraft actually renders that many pixels. No off-screen supersampling is implemented, and nothing is upscaled. 8K is therefore effectively unavailable on phones.
* **Hardware encoding probably will not work in Pojav**: FFmpeg's MediaCodec wrapper needs an ART `JavaVM`, which Pojav's desktop-style JVM does not provide. The probe falls back to software x264, whose capacity is estimated from CPU core count (heuristic, not measured). Expect 720p30 to be comfortable and 1080p30+ to cost FPS/heat on many phones.
* **Audio is a faithful approximation, not a tap of OpenAL's output**: no reverb, doppler or occlusion; Mojang's exact attenuation curve is approximated; sounds started before recording began are not included (music already playing joins at its next track).
* **The recording indicator and ESC menu are part of the rendered frame**, so they appear in the video. Turn the indicator off in Interface for clean footage. PojavLauncher's touch controls are an Android overlay and are *not* in the framebuffer; Minecraft-rendered HUD, crosshair and menus are.
* Storage uses normal file paths. Android's Storage Access Framework / MediaStore are not reachable from the JVM; Pojav needs "all files"/storage permission for `/storage/emulated/0/...`, otherwise it falls back to the game folder.
* Bundled FFmpeg (JavaCPP preset) is GPL-licensed (libx264) → this mod is GPL-3.0-or-later. Microphone recording is not implemented (it was optional).
* Per-frame `copyPixelsArgb()` allocates one int[] per captured frame; a pooled/native-pointer copy would be a worthwhile optimization.

## Known risk points to verify against 1.21.11 yarn (`genSources`)
1. `MinecraftClient.render(boolean)` (frame hook).
2. `SoundSystem.play(SoundInstance)` return type `SoundSystem$PlayResult`, `stop`, `stopAll`, `pauseAll`, `resumeAll` — sound hooks use `require=0`, so a mismatch gives video-only recording instead of a crash (check the log for `eventsSeen`).
3. `NativeImage.copyPixelsArgb()`, `ScreenshotRecorder.takeScreenshot(Framebuffer, Consumer<NativeImage>)`.
4. `KeyBinding.Category.create(Identifier)` and the 5-arg `KeyBinding` constructor.
5. `AudioStream.getBuffer(int)`, `Sound.getLocation()`, `GameOptions.getSoundVolume(SoundCategory)`.
6. `ButtonWidget` protected constructor / `renderWidget`, `HudRenderCallback`, `SystemToast.Type()`.
7. JavaCV: `FFmpegFrameRecorder.setVideoCodecName`, `setOption("movflags", …)`.

## Test checklist (on device)
1. Self-test: natives OK? encoder name? 2. Record 30 s at Low (480p30) → play in the phone gallery and VLC. 3. Same at 720p30, then 1080p. 4. Break a sound (explosion, note block, music disc): audible and aligned with the picture? Adjust A/V offset if not. 5. Kill the game mid-recording: file should still play. 6. Select 4K: you should get a clear refusal + suggestion. 7. Toggle with keybind and ESC button in turn: never two sessions.

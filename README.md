# KingCapture

**Client-side Fabric recording mod for Minecraft 1.21.11.** KingCapture captures Minecraft's framebuffer to MP4 (H.264 + AAC when the bundled FFmpeg runtime supports it), provides a toggle keybind, and adds recording controls plus a local recordings library to the in-world pause menu.

## Pause menu

Open a world and press **Esc**. The bottom of the pause menu has:

- **Start Recording / Stop Recording** — the same button changes with the recorder state.
- **Recordings** — opens the MP4 library. **Watch** opens the recording in PojavLauncher's Android file/video handler (or the desktop default player); **Download** copies it to `Download/KingCapture` on Android, or the game folder fallback.

This is an MP4 file library, not a replay timeline. KingCapture does not depend on ReplayMod, use ReplayMod APIs, or read/write ReplayMod replay files.

## Mod Menu

With the matching Mod Menu build installed, **Mod Menu → KingCapture → Config** opens the configuration screen. Mod Menu is optional; recording and pause-menu controls work without it.

Settings include resolution, frame rate, video quality, bitrate, encoder selection, audio capture, output directory, performance presets, indicator visibility, and pause-menu button visibility. The keybind **KingCapture: Toggle Recording** is unbound by default.

## Build

Use JDK 21 and Gradle 9.2 or newer:

```sh
gradle --no-daemon clean build
```

The installable JAR is expected at `build/libs/kingcapture-1.0.0.jar`. The GitHub Actions workflow builds and uploads this file as an artifact.

## Install

Use a Minecraft 1.21.11 Fabric profile with Java 21. Put the JAR in the profile's `mods` folder. Fabric API is required; Mod Menu is optional and only needed for its config screen.

## Recording notes

- The selected output resolution cannot exceed the game's actual framebuffer size. The mod does not invent detail by upscaling, and 4K/8K are only available if the actual rendered size and encoder can support them.
- On PojavLauncher, KingCapture tries the Pojav Android open-file bridge to open MP4s in the device's video player. Other environments use the desktop file-opening handler when available.
- The Download action copies an existing recording to a dedicated Downloads folder; it does not download content from a server.
- PojavLauncher touch overlays rendered outside Minecraft are not part of the framebuffer and cannot be hidden by an ordinary Fabric mod.
- Audio capture is a software mix of Minecraft sound resources and is an approximation of the final OpenAL output. Verify audio capture on the target runtime.
- JavaCV/FFmpeg native loading and recording performance must be tested on the target Android device before treating any resolution/FPS combination as supported.

## Dependencies

- Minecraft Java Edition 1.21.11
- Fabric Loader and Fabric API
- Java 21
- Optional Mod Menu 17.0.0 for the configuration screen

No ReplayMod dependency or integration is included.

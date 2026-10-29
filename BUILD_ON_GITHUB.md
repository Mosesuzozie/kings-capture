# Build KingCapture into a mod JAR

This is the **source project**, not a prebuilt/installable JAR. The current workspace does not have Gradle or network access to download Minecraft/Fabric/FFmpeg build dependencies, so a genuine JAR cannot be produced or tested here.

## Build automatically with GitHub Actions

1. Extract this ZIP on your computer.
2. Create a new GitHub repository for KingCapture.
3. Upload the **contents of this folder** to the repository root (including the hidden `.github/workflows/build.yml` file), and commit to `main`.
4. Open the repository's **Actions** tab and select **Build KingCapture**. It also runs automatically when you push to `main`/`master`.
5. If the build succeeds, open the completed workflow run and download **kingcapture-minecraft-1.21.11** under **Artifacts**.
6. Install the downloaded `kingcapture-1.0.0.jar` in the Minecraft 1.21.11 Fabric `mods` folder only after the build succeeds.

The workflow uses Java 21 and Gradle 9.2 because Fabric Loom 1.14 requires Gradle 9.2 or newer. It will compile the code against the declared Minecraft/Fabric dependencies and upload the output only if the build passes.

## Important

The source README identifies Minecraft 1.21.11 API names that still need build/device verification. This workflow does **not** guarantee that the code compiles or that FFmpeg native loading, video capture, audio capture, or MP4 playback works on PojavLauncher. If the workflow fails, open the failed run, copy the first relevant `error:`/`Could not resolve` section from the build log, and fix that before installing.

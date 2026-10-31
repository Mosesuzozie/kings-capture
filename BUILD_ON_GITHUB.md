# Build KingCapture on GitHub Actions

The repository must contain the complete project, including `src/main/java`, `src/main/resources`, the Gradle files, and `.github/workflows/build.yml`.

1. Upload the **contents** of this project folder to the root of the GitHub repository (not an extra nested `kingcapture/` folder).
2. Commit the upload to `main`.
3. Open **Actions → Build KingCapture** and open the latest run.
4. If the build succeeds, download the **KingCapture-1.0.0** artifact and extract it. The JAR is `kingcapture-1.0.0.jar`.
5. Install it in a Fabric 1.21.11 client with Java 21 and Fabric API. Mod Menu is optional.

A successful Gradle build only confirms that compilation and packaging passed. It does not prove that FFmpeg native loading, video capture, game-audio mixing, or PojavLauncher playback works on every device. Test those on the target phone.

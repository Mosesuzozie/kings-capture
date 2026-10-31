package dev.kingcapture.ui;

import dev.kingcapture.config.KcConfig;
import dev.kingcapture.core.RecordingController;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

/** Small blinking dot + timer + file size, top-right. Note: it is part of the rendered frame, so it is also in the video. */
public final class HudOverlay {
    private static long lastSizeCheck; private static long cachedBytes;

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            KcConfig c = KcConfig.get();
            RecordingController.State s = RecordingController.state();
            if (!c.showIndicator || s == RecordingController.State.IDLE) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            String text;
            if (s == RecordingController.State.RECORDING) {
                long now = System.currentTimeMillis();
                if (now - lastSizeCheck > 1000) { cachedBytes = RecordingController.fileBytes(); lastSizeCheck = now; }
                text = c.showTimer ? "REC " + RecordingController.fmtTime(RecordingController.elapsedMillis())
                        + "  " + String.format(java.util.Locale.ROOT, "%.1f MB", cachedBytes / 1_000_000.0) : "REC";
            } else text = s == RecordingController.State.STARTING ? "Starting\u2026" : "Saving\u2026";
            int w = tr.getWidth(text);
            int x = ctx.getScaledWindowWidth() - w - 8, y = 6;
            if ((System.currentTimeMillis() / 500) % 2 == 0 || s != RecordingController.State.RECORDING)
                ctx.fill(x - 10, y + 1, x - 4, y + 7, 0xFFE02020);
            ctx.drawTextWithShadow(tr, text, x, y, 0xFFFFFFFF);
        });
    }
}

package dev.kingcapture.ui;

import dev.kingcapture.config.KcConfig;
import dev.kingcapture.core.NativeLoader;
import dev.kingcapture.core.RecordingController;
import dev.kingcapture.core.StoragePaths;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.function.Supplier;

/** Dependency-free settings screen (vanilla widgets only; no Cloth Config). Five tabs, one column. */
public class KcConfigScreen extends Screen {
    private enum Tab { RECORDING("Recording"), AUDIO("Audio"), STORAGE("Storage"), PERFORMANCE("Performance"), INTERFACE("Interface");
        final String label; Tab(String l) { label = l; } }

    private static Tab tab = Tab.RECORDING;
    private final Screen parent;
    private int rowY;

    public KcConfigScreen(Screen parent) { super(Text.literal("KingCapture Settings")); this.parent = parent; }

    @Override
    protected void init() {
        KcConfig c = KcConfig.get();
        Tab[] tabs = Tab.values();
        int tw = Math.min(90, (width - 10) / tabs.length);
        int tx = (width - tw * tabs.length) / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), x -> { tab = t; clearAndInit(); })
                    .dimensions(tx + i * tw, 8, tw, 20).build();
            b.active = t != tab;
            addDrawableChild(b);
        }
        rowY = 40;
        switch (tab) {
            case RECORDING -> {
                row(() -> "Resolution: " + c.resolution.label, () -> { c.resolution = KcConfig.cycle(c.resolution); clearAndInit(); });
                if (c.resolution == KcConfig.Res.CUSTOM) {
                    field(width / 2 - 130, rowY, "Width", String.valueOf(c.customWidth), v -> c.customWidth = num(v, c.customWidth));
                    field(width / 2 + 10, rowY, "Height", String.valueOf(c.customHeight), v -> c.customHeight = num(v, c.customHeight));
                    rowY += 24;
                }
                row(() -> "Frame rate: " + c.fps.value + " FPS" + (c.fps == KcConfig.Fps.F120 ? " (if supported)" : ""), () -> c.fps = KcConfig.cycle(c.fps));
                row(() -> "Video quality: " + c.quality.label, () -> c.quality = KcConfig.cycle(c.quality));
                int[] br = {0, 1000, 2000, 4000, 8000, 12000, 20000, 30000, 50000, 80000};
                row(() -> "Bitrate: " + (c.bitrateKbps == 0 ? "Auto" : c.bitrateKbps / 1000.0 + " Mbps"), () -> {
                    int i = 0; for (int k = 0; k < br.length; k++) if (br[k] == c.bitrateKbps) i = k;
                    c.bitrateKbps = br[(i + 1) % br.length]; });
                String[] enc = {"auto", "h264_mediacodec", "libx264", "libopenh264"};
                row(() -> "Encoder: " + c.encoder, () -> {
                    int i = 0; for (int k = 0; k < enc.length; k++) if (enc[k].equals(c.encoder)) i = k;
                    c.encoder = enc[(i + 1) % enc.length]; });
            }
            case AUDIO -> {
                row(() -> "Game audio: " + onOff(c.audioEnabled), () -> c.audioEnabled = !c.audioEnabled);
                int[] kb = {64, 96, 128, 192, 256, 320};
                row(() -> "Audio quality: " + c.audioKbps + " kbps AAC", () -> {
                    int i = 0; for (int k = 0; k < kb.length; k++) if (kb[k] == c.audioKbps) i = k;
                    c.audioKbps = kb[(i + 1) % kb.length]; });
                int[] off = {-200, -100, -50, 0, 50, 100, 200, 300};
                row(() -> "A/V sync offset: " + (c.audioOffsetMs > 0 ? "+" : "") + c.audioOffsetMs + " ms", () -> {
                    int i = 3; for (int k = 0; k < off.length; k++) if (off[k] == c.audioOffsetMs) i = k;
                    c.audioOffsetMs = off[(i + 1) % off.length]; });
            }
            case STORAGE -> {
                field(width / 2 - 130, rowY, "Output folder (auto = Movies/KingCapture)", c.outputDir, v -> c.outputDir = v.isBlank() ? "auto" : v.trim(), 260);
                rowY += 26;
                field(width / 2 - 130, rowY, "Filename format", c.filenameFormat, v -> c.filenameFormat = v.isBlank() ? "KingCapture_{date}_{time}" : v, 260);
                rowY += 26;
                row(() -> "Auto timestamp in name: " + onOff(c.autoTimestamp), () -> c.autoTimestamp = !c.autoTimestamp);
            }
            case PERFORMANCE -> {
                int bw = Math.min(70, (width - 20) / 4);
                int bx = (width - bw * 4) / 2;
                KcConfig.Preset[] ps = KcConfig.Preset.values();
                String[] names = {"Low", "Balanced", "High", "Maximum"};
                for (int i = 0; i < 4; i++) {
                    KcConfig.Preset p = ps[i];
                    addDrawableChild(ButtonWidget.builder(Text.literal(names[i]), b -> { c.applyPreset(p); }).dimensions(bx + i * bw, rowY, bw, 20).build());
                }
                rowY += 24;
                row(() -> "Priority: " + switch (c.mode) { case QUALITY -> "Video quality"; case BALANCED -> "Balanced"; case SMOOTH -> "Smooth gameplay"; },
                        () -> c.mode = KcConfig.cycle(c.mode));
                row(() -> "Performance warnings: " + onOff(c.warnings), () -> c.warnings = !c.warnings);
                row(() -> "Memory safeguards: " + onOff(c.memorySafeguard), () -> c.memorySafeguard = !c.memorySafeguard);
                row(() -> "Try hardware encoder: " + onOff(c.tryHardware), () -> c.tryHardware = !c.tryHardware);
                addDrawableChild(ButtonWidget.builder(Text.literal("Run self-test (natives + encoder)"), b -> RecordingController.runSelfTest())
                        .dimensions(width / 2 - 130, rowY, 260, 20).build());
                rowY += 24;
            }
            case INTERFACE -> {
                row(() -> "Recording indicator: " + onOff(c.showIndicator), () -> c.showIndicator = !c.showIndicator);
                row(() -> "Recording timer: " + onOff(c.showTimer), () -> c.showTimer = !c.showTimer);
                row(() -> "ESC recording button: " + onOff(c.showEscButton), () -> c.showEscButton = !c.showEscButton);
                row(() -> "ESC Recordings button: " + onOff(c.showRecordingsButton), () -> c.showRecordingsButton = !c.showRecordingsButton);
                row(() -> "Status messages: " + onOff(c.statusMessages), () -> c.statusMessages = !c.statusMessages);
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(width / 2 - 60, height - 28, 120, 20).build());
    }

    private void row(Supplier<String> label, Runnable click) {
        ButtonWidget real = ButtonWidget.builder(Text.literal(label.get()), x -> { click.run(); x.setMessage(Text.literal(label.get())); })
                .dimensions(width / 2 - 130, rowY, 260, 20).build();
        addDrawableChild(real);
        rowY += 24;
    }

    private TextFieldWidget field(int x, int y, String hint, String value, java.util.function.Consumer<String> onChange) { return field(x, y, hint, value, onChange, 120); }

    private TextFieldWidget field(int x, int y, String hint, String value, java.util.function.Consumer<String> onChange, int w) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(hint));
        f.setMaxLength(256);
        f.setText(value);
        f.setPlaceholder(Text.literal(hint));
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return f;
    }

    private static int num(String s, int old) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return old; } }
    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int y = Math.min(height - 62, rowY + 6);
        if (tab == Tab.STORAGE) {
            try {
                Path dir = StoragePaths.resolveOutputDir(KcConfig.get());
                long free = StoragePaths.freeBytes(dir);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Saving to: " + dir), width / 2, y, 0xFFAAAAAA);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Free space: " + (free < 0 ? "unknown" : free / 1_000_000 + " MB")), width / 2, y + 12, 0xFFAAAAAA);
            } catch (Exception e) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Folder not usable: " + e.getMessage()), width / 2, y, 0xFFFF5555);
            }
        } else if (tab == Tab.PERFORMANCE) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("FFmpeg natives: " + NativeLoader.status()), width / 2, y, 0xFFAAAAAA);
        } else if (tab == Tab.RECORDING) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Capture size is limited to the game's render size (no upscaling)."), width / 2, y, 0xFFAAAAAA);
        }
    }

    @Override public void removed() { KcConfig.save(); }
    @Override public void close() { KcConfig.save(); if (client != null) client.setScreen(parent); }
}

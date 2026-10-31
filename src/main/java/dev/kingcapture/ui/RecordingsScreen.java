package dev.kingcapture.ui;

import dev.kingcapture.core.RecordingController;
import dev.kingcapture.core.RecordingFiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Browse recorded MP4 files, watch them with the device player, or copy them to Downloads. */
public final class RecordingsScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private final Screen parent;
    private List<Path> recordings = List.of();
    private String loadError;
    private int page;

    public RecordingsScreen(Screen parent) {
        super(Text.literal("KingCapture Recordings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshFiles();
        int first = page * PAGE_SIZE;
        int last = Math.min(recordings.size(), first + PAGE_SIZE);
        int rowY = 58;
        for (int i = first; i < last; i++) {
            Path file = recordings.get(i);
            int y = rowY + (i - first) * 31;
            int actionWidth = Math.max(54, Math.min(68, (width - 170) / 2));
            int watchX = width - actionWidth * 2 - 16;
            int copyX = width - actionWidth - 8;
            addDrawableChild(ButtonWidget.builder(Text.literal("Watch"), button -> watch(file))
                    .dimensions(watchX, y, actionWidth, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Download"), button -> download(file))
                    .dimensions(copyX, y, actionWidth, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> { page = 0; clearAndInit(); })
                .dimensions(8, height - 30, 68, 20).build());
        if (page > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> { page--; clearAndInit(); })
                    .dimensions(width / 2 - 104, height - 30, 96, 20).build());
        }
        if ((page + 1) * PAGE_SIZE < recordings.size()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> { page++; clearAndInit(); })
                    .dimensions(width / 2 + 8, height - 30, 96, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(width - 76, height - 30, 68, 20).build());
    }

    private void refreshFiles() {
        try {
            recordings = RecordingFiles.list();
            loadError = null;
            int pages = Math.max(1, (recordings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.min(page, pages - 1);
        } catch (IOException ex) {
            recordings = List.of();
            loadError = ex.getMessage();
        }
    }

    private void watch(Path file) {
        try {
            RecordingFiles.watch(file);
            RecordingController.toast("KingCapture", "Opened " + file.getFileName() + " in the video player.", false);
        } catch (IOException ex) {
            RecordingController.toast("KingCapture: cannot play video", ex.getMessage(), true);
        }
    }

    private void download(Path file) {
        try {
            Path copy = RecordingFiles.copyToDownloads(file);
            RecordingController.toast("KingCapture: copied to Downloads", copy.toString(), false);
        } catch (IOException ex) {
            RecordingController.toast("KingCapture: download failed", ex.getMessage(), true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Page " + (page + 1) + "  •  " + recordings.size() + " MP4 recording(s)"),
                width / 2, 36, 0xFFBBBBBB);

        if (loadError != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Could not load recordings: " + loadError),
                    width / 2, height / 2, 0xFFFF6666);
        } else if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No MP4 recordings yet."),
                    width / 2, height / 2 - 8, 0xFFDDDDDD);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Start a recording from the pause menu first."),
                    width / 2, height / 2 + 8, 0xFFAAAAAA);
        } else {
            int first = page * PAGE_SIZE;
            int last = Math.min(recordings.size(), first + PAGE_SIZE);
            for (int i = first; i < last; i++) {
                Path file = recordings.get(i);
                int y = 64 + (i - first) * 31;
                String name = file.getFileName().toString();
                int maxTextWidth = Math.max(60, width - 170);
                String fitted = textRenderer.trimToWidth(name, maxTextWidth);
                long bytes = RecordingFiles.size(file);
                String size = bytes >= 1_000_000
                        ? String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
                        : String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0);
                context.drawTextWithShadow(textRenderer, fitted, 10, y + 3, 0xFFFFFFFF);
                context.drawTextWithShadow(textRenderer, size, 10, y + 15, 0xFFAAAAAA);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}

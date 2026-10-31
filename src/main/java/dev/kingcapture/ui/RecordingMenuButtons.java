package dev.kingcapture.ui;

import dev.kingcapture.core.RecordingController;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.lang.ref.WeakReference;

/** Keeps the ESC-menu recording button label synchronized with the single recorder state. */
public final class RecordingMenuButtons {
    private static WeakReference<ButtonWidget> toggleButton = new WeakReference<>(null);

    private RecordingMenuButtons() { }

    public static void attach(ButtonWidget button) {
        toggleButton = new WeakReference<>(button);
        refresh();
    }

    public static void refresh() {
        ButtonWidget button = toggleButton.get();
        if (button == null) return;
        RecordingController.State state = RecordingController.state();
        button.setMessage(RecordingController.buttonLabel());
        button.active = state == RecordingController.State.IDLE || state == RecordingController.State.RECORDING;
    }
}

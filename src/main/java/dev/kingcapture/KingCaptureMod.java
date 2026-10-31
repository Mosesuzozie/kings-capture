package dev.kingcapture;

import dev.kingcapture.config.KcConfig;
import dev.kingcapture.core.RecordingController;
import dev.kingcapture.ui.HudOverlay;
import dev.kingcapture.ui.RecordingsScreen;
import dev.kingcapture.ui.RecordingMenuButtons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KingCaptureMod implements ClientModInitializer {
    public static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        KcConfig.load();

        // Deliberately unbound. The player can assign a key in Options > Controls.
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingcapture.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                KeyBinding.Category.create(Identifier.of("kingcapture", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                while (toggleKey.wasPressed()) { }
                RecordingController.toggle();
            }
            RecordingMenuButtons.refresh();
        });

        // Fabric's screen event is used instead of a hard-coded GameMenuScreen mixin. This runs
        // after vanilla and other mods initialize the pause screen, so both buttons are attached
        // to the actual widget list and survive screen resizing.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen)) return;

            boolean showRecorder = KcConfig.get().showEscButton;
            boolean showLibrary = KcConfig.get().showRecordingsButton;
            int gap = 6;
            int buttonWidth = Math.min(154, Math.max(90, (scaledWidth - 22 - gap) / 2));
            int count = (showRecorder ? 1 : 0) + (showLibrary ? 1 : 0);
            int groupWidth = count == 2 ? buttonWidth * 2 + gap : buttonWidth;
            int left = (scaledWidth - groupWidth) / 2;
            int y = Math.max(8, scaledHeight - 32);

            if (showRecorder) {
                ButtonWidget record = ButtonWidget.builder(RecordingController.buttonLabel(), button -> RecordingController.toggle())
                        .dimensions(left, y, buttonWidth, 20).build();
                screen.addDrawableChild(record);
                RecordingMenuButtons.attach(record);
            }

            if (showLibrary) {
                int recordingsX = left + (showRecorder ? buttonWidth + gap : 0);
                ButtonWidget recordings = ButtonWidget.builder(Text.literal("Recordings"),
                                button -> client.setScreen(new RecordingsScreen(screen)))
                        .dimensions(recordingsX, y, buttonWidth, 20).build();
                screen.addDrawableChild(recordings);
            }
        });

        HudOverlay.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RecordingController.shutdownBlocking());
        System.out.println("[KingCapture] loaded; pause-menu buttons and Mod Menu integration registered");
    }
}

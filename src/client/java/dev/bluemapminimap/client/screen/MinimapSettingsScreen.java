package dev.bluemapminimap.client.screen;

import dev.bluemapminimap.client.BlueMapRuntime;
import dev.bluemapminimap.client.ConnectionStatus;
import dev.bluemapminimap.config.HudPosition;
import dev.bluemapminimap.config.MinimapConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class MinimapSettingsScreen extends Screen {
    private static final int[] SIZES = {80, 112, 144, 176, 208, 240};
    private static final double[] ZOOMS = {1.0, 2.0, 4.0};

    private final Screen parent;
    private final BlueMapRuntime runtime;
    private final MinimapConfig edited;

    public MinimapSettingsScreen(Screen parent, BlueMapRuntime runtime) {
        super(Component.translatable("bluemap_minimap.settings"));
        this.parent = parent;
        this.runtime = runtime;
        this.edited = runtime.config().copy();
    }

    @Override
    protected void init() {
        int left = width / 2 - 102;
        int y = Math.max(70, height / 4);
        Button enabled = addRenderableWidget(Button.builder(enabledText(), button -> {
            edited.enabled = !edited.enabled;
            runtime.previewConfig(edited);
            button.setMessage(enabledText());
        }).bounds(left, y, 204, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(sizeText(), button -> {
            edited.size = nextInt(SIZES, edited.size);
            runtime.previewConfig(edited);
            button.setMessage(sizeText());
        }).bounds(left, y, 204, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(positionText(), button -> {
            edited.position = edited.position.next();
            runtime.previewConfig(edited);
            button.setMessage(positionText());
        }).bounds(left, y, 204, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(zoomText(), button -> {
            edited.zoom = nextDouble(ZOOMS, edited.zoom);
            runtime.previewConfig(edited);
            button.setMessage(zoomText());
        }).bounds(left, y, 204, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(playersText(), button -> {
            edited.showPlayers = !edited.showPlayers;
            runtime.previewConfig(edited);
            button.setMessage(playersText());
        }).bounds(left, y, 100, 20).build());
        addRenderableWidget(Button.builder(namesText(), button -> {
            edited.showNames = !edited.showNames;
            runtime.previewConfig(edited);
            button.setMessage(namesText());
        }).bounds(left + 104, y, 100, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("bluemap_minimap.reload"), button -> {
            runtime.reloadConfig();
            minecraft.gui.setScreen(parent);
        }).bounds(left, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("bluemap_minimap.done"), button -> onClose())
                .bounds(left + 104, y, 100, 20).build());
        enabled.active = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("bluemap_minimap.config_hint"), width / 2, 32, 0xFFB7C3D0);
        graphics.centeredText(font, Component.translatable("bluemap_minimap.current",
                edited.size,
                Component.translatable(positionKey(edited.position)),
                String.format(Locale.ROOT, "%.1f", edited.zoom),
                booleanText(edited.showPlayers),
                booleanText(edited.showNames)), width / 2, 48, 0xFFFFFFFF);
        ConnectionStatus status = runtime.status();
        graphics.centeredText(font, Component.translatable("bluemap_minimap.status", status.mode(), status.mapId(),
                status.tile(), status.queuedDownloads(), status.cachedTiles()), width / 2, height - 18, 0xFFB7C3D0);
    }

    @Override
    public void onClose() {
        runtime.saveConfig(edited);
        minecraft.gui.setScreen(parent);
    }

    private Component enabledText() {
        return Component.translatable("bluemap_minimap.enabled", booleanText(edited.enabled));
    }

    private Component sizeText() {
        return Component.translatable("bluemap_minimap.size", edited.size);
    }

    private Component positionText() {
        return Component.translatable("bluemap_minimap.position", Component.translatable(positionKey(edited.position)));
    }

    private Component zoomText() {
        return Component.translatable("bluemap_minimap.zoom", String.format(Locale.ROOT, "%.1f", edited.zoom));
    }

    private Component playersText() {
        return Component.translatable("bluemap_minimap.players", booleanText(edited.showPlayers));
    }

    private Component namesText() {
        return Component.translatable("bluemap_minimap.names", booleanText(edited.showNames));
    }

    private static Component booleanText(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static String positionKey(HudPosition position) {
        return switch (position) {
            case TOP_RIGHT -> "bluemap_minimap.top_right";
            case TOP_LEFT -> "bluemap_minimap.top_left";
            case BOTTOM_RIGHT -> "bluemap_minimap.bottom_right";
            case BOTTOM_LEFT -> "bluemap_minimap.bottom_left";
        };
    }

    private static int nextInt(int[] values, int current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private static double nextDouble(double[] values, double current) {
        for (int i = 0; i < values.length; i++) if (Double.compare(values[i], current) == 0) return values[(i + 1) % values.length];
        return values[0];
    }
}

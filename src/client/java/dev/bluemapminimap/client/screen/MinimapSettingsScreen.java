package dev.bluemapminimap.client.screen;

import dev.bluemapminimap.client.BlueMapRuntime;
import dev.bluemapminimap.client.ConnectionStatus;
import dev.bluemapminimap.config.HudPosition;
import dev.bluemapminimap.config.MapOrientation;
import dev.bluemapminimap.config.MinimapConfig;
import dev.bluemapminimap.i18n.UiTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class MinimapSettingsScreen extends Screen {
    private static final double[] ZOOMS = {1.0, 2.0, 4.0};

    private final Screen parent;
    private final BlueMapRuntime runtime;
    private final MinimapConfig edited;

    public MinimapSettingsScreen(Screen parent, BlueMapRuntime runtime) {
        super(UiTranslations.component(selectedLanguage(), "bluemap_minimap.settings"));
        this.parent = parent;
        this.runtime = runtime;
        this.edited = runtime.config().copy();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(320, width - 20);
        int gap = 4;
        int halfWidth = (panelWidth - gap) / 2;
        int left = (width - panelWidth) / 2;
        int y = Math.max(55, height / 5);
        Button enabled = addButton(Button.builder(enabledText(), button -> {
            edited.enabled = !edited.enabled;
            runtime.previewConfig(edited);
            button.setMessage(enabledText());
        }).bounds(left, y, panelWidth, 20).build(), "bluemap_minimap.tooltip.enabled");
        y += 22;
        addButton(Button.builder(sizeText(), button -> {
            edited.size = nextInt(MinimapConfig.SIZE_OPTIONS, edited.size);
            runtime.previewConfig(edited);
            button.setMessage(sizeText());
        }).bounds(left, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.size");
        addButton(Button.builder(zoomText(), button -> {
            edited.zoom = nextDouble(ZOOMS, edited.zoom);
            runtime.previewConfig(edited);
            button.setMessage(zoomText());
        }).bounds(left + halfWidth + gap, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.zoom");
        y += 22;
        addButton(Button.builder(positionText(), button -> {
            edited.position = edited.position.next();
            runtime.previewConfig(edited);
            button.setMessage(positionText());
        }).bounds(left, y, panelWidth, 20).build(), "bluemap_minimap.tooltip.position");
        y += 22;
        addButton(Button.builder(orientationText(), button -> {
            edited.mapOrientation = edited.mapOrientation.next();
            runtime.previewConfig(edited);
            button.setMessage(orientationText());
        }).bounds(left, y, panelWidth, 20).build(), "bluemap_minimap.tooltip.orientation");
        y += 22;
        addButton(Button.builder(playersText(), button -> {
            edited.showPlayers = !edited.showPlayers;
            runtime.previewConfig(edited);
            button.setMessage(playersText());
        }).bounds(left, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.players");
        addButton(Button.builder(namesText(), button -> {
            edited.showNames = !edited.showNames;
            runtime.previewConfig(edited);
            button.setMessage(namesText());
        }).bounds(left + halfWidth + gap, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.names");
        y += 22;
        addButton(Button.builder(compassText(), button -> {
            edited.showCompass = !edited.showCompass;
            runtime.previewConfig(edited);
            button.setMessage(compassText());
        }).bounds(left, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.compass");
        addButton(Button.builder(coordinatesText(), button -> {
            edited.showCoordinates = !edited.showCoordinates;
            runtime.previewConfig(edited);
            button.setMessage(coordinatesText());
        }).bounds(left + halfWidth + gap, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.coordinates");
        y += 26;
        addButton(Button.builder(text("bluemap_minimap.cancel"), button -> {
            runtime.reloadConfig();
            minecraft.gui.setScreen(parent);
        }).bounds(left, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.cancel");
        addButton(Button.builder(text("bluemap_minimap.done"), button -> onClose())
                .bounds(left + halfWidth + gap, y, halfWidth, 20).build(), "bluemap_minimap.tooltip.done");
        enabled.active = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, text("bluemap_minimap.config_hint"), width / 2, 32, 0xFFB7C3D0);
        graphics.centeredText(font, text("bluemap_minimap.current",
                edited.size,
                text(positionKey(edited.position)),
                text(orientationKey(edited.mapOrientation))), width / 2, 42, 0xFFFFFFFF);
        ConnectionStatus status = runtime.status();
        graphics.centeredText(font, text("bluemap_minimap.status", status.mode(), status.mapId(),
                status.tile(), status.queuedDownloads(), status.cachedTiles()), width / 2, height - 18, 0xFFB7C3D0);
    }

    @Override
    public void onClose() {
        runtime.saveConfig(edited);
        minecraft.gui.setScreen(parent);
    }

    private Component enabledText() {
        return text("bluemap_minimap.enabled", enabledValueText(edited.enabled));
    }

    private Component sizeText() {
        return text("bluemap_minimap.size", edited.size);
    }

    private Component positionText() {
        return text("bluemap_minimap.position", text(positionKey(edited.position)));
    }

    private Component zoomText() {
        return text("bluemap_minimap.zoom",
                text(zoomKey(edited.zoom)), String.format(Locale.ROOT, "%.1f", edited.zoom));
    }

    private Component playersText() {
        return text("bluemap_minimap.players", visibilityText(edited.showPlayers));
    }

    private Component namesText() {
        return text("bluemap_minimap.names", visibilityText(edited.showNames));
    }

    private Component orientationText() {
        return text("bluemap_minimap.orientation", text(orientationKey(edited.mapOrientation)));
    }

    private Component compassText() {
        return text("bluemap_minimap.compass", visibilityText(edited.showCompass));
    }

    private Component coordinatesText() {
        return text("bluemap_minimap.coordinates", visibilityText(edited.showCoordinates));
    }

    private Button addButton(Button button, String tooltipKey) {
        button.setTooltip(Tooltip.create(text(tooltipKey)));
        return addRenderableWidget(button);
    }

    private Component enabledValueText(boolean value) {
        return text(value
                ? "bluemap_minimap.value.enabled" : "bluemap_minimap.value.disabled");
    }

    private Component visibilityText(boolean value) {
        return text(value
                ? "bluemap_minimap.value.visible" : "bluemap_minimap.value.hidden");
    }

    private Component text(String key, Object... arguments) {
        return UiTranslations.component(selectedLanguage(), key, arguments);
    }

    private static String selectedLanguage() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? "en_us" : client.getLanguageManager().getSelected();
    }

    private static String zoomKey(double zoom) {
        if (Double.compare(zoom, 1.0) == 0) return "bluemap_minimap.zoom.near";
        if (Double.compare(zoom, 4.0) == 0) return "bluemap_minimap.zoom.wide";
        return "bluemap_minimap.zoom.standard";
    }

    private static String positionKey(HudPosition position) {
        return switch (position) {
            case TOP_RIGHT -> "bluemap_minimap.top_right";
            case TOP_LEFT -> "bluemap_minimap.top_left";
            case BOTTOM_RIGHT -> "bluemap_minimap.bottom_right";
            case BOTTOM_LEFT -> "bluemap_minimap.bottom_left";
        };
    }

    private static String orientationKey(MapOrientation orientation) {
        return orientation == MapOrientation.HEADING_UP
                ? "bluemap_minimap.heading_up" : "bluemap_minimap.north_up";
    }

    private static int nextInt(List<Integer> values, int current) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == current) return values.get((i + 1) % values.size());
        }
        return values.getFirst();
    }

    private static double nextDouble(double[] values, double current) {
        for (int i = 0; i < values.length; i++) if (Double.compare(values[i], current) == 0) return values[(i + 1) % values.length];
        return values[0];
    }
}

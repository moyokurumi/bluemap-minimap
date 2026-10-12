package dev.bluemapminimap.client.screen;

import dev.bluemapminimap.client.BlueMapRuntime;
import dev.bluemapminimap.config.MinimapConfig;
import dev.bluemapminimap.config.ServerProfile;
import dev.bluemapminimap.config.ServerProfileEditor;
import dev.bluemapminimap.i18n.UiTranslations;
import dev.bluemapminimap.model.ClientObservation;
import dev.bluemapminimap.protocol.BlueMapDiscovery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Edits the parent screen's draft. Only the parent's Done/Esc writes configuration. */
public final class ServerSettingsScreen extends Screen {
    private final Screen parent;
    private final BlueMapRuntime runtime;
    private final MinimapConfig draft;
    private String address;
    private String url;
    private String dimension;
    private String selectedMap;
    private boolean discover;
    private List<String> maps = List.of();
    private String message = "bluemap_minimap.setup.hint";
    private boolean loading;
    private int requestGeneration;
    private int left;
    private Button mapButton;
    private Button loadButton;

    public ServerSettingsScreen(Screen parent, BlueMapRuntime runtime, MinimapConfig draft) {
        super(text("bluemap_minimap.server_settings"));
        this.parent = parent;
        this.runtime = runtime;
        this.draft = draft;
        ClientObservation observation = runtime.observation();
        address = observation == null ? "" : observation.serverAddress();
        dimension = observation == null ? "minecraft:overworld" : observation.dimension();
        ServerProfile profile = draft.profileFor(address);
        url = profile == null ? "" : profile.blueMapUrl;
        selectedMap = profile == null ? "" : profile.dimensions.getOrDefault(dimension, "");
        discover = draft.autoDiscoverBlueMap;
        BlueMapDiscovery.Catalog found = runtime.discoveredFor(address);
        if (url.isBlank() && found != null) url = found.base().toString();
        if (found != null && found.base().toString().equals(url)) maps = found.maps();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, width - 20);
        int halfWidth = (panelWidth - 4) / 2;
        left = (width - panelWidth) / 2;
        EditBox server = field(left, 47, panelWidth, 255, "bluemap_minimap.setup.server", address);
        EditBox endpoint = field(left, 80, panelWidth, 2048, "bluemap_minimap.setup.url", url);
        endpoint.setHint(Component.literal("https://map.example.com/"));
        endpoint.setResponder(value -> {
            if (!value.equals(url)) {
                url = value;
                maps = List.of();
                selectedMap = "";
                requestGeneration++;
                if (mapButton != null) mapButton.setMessage(mapText());
            }
        });
        EditBox world = field(left, 113, panelWidth, 255, "bluemap_minimap.setup.dimension", dimension);
        world.setResponder(value -> {
            dimension = value;
            ServerProfile profile = draft.profileFor(address);
            selectedMap = profile == null ? "" : profile.dimensions.getOrDefault(dimension, "");
            if (mapButton != null) mapButton.setMessage(mapText());
        });
        server.setResponder(value -> {
            address = value;
            requestGeneration++;
            ServerProfile profile = draft.profileFor(address);
            BlueMapDiscovery.Catalog found = runtime.discoveredFor(address);
            endpoint.setValue(profile != null ? profile.blueMapUrl : found != null ? found.base().toString() : "");
            maps = found != null && found.base().toString().equals(url) ? found.maps() : List.of();
            selectedMap = profile == null ? "" : profile.dimensions.getOrDefault(dimension, "");
            if (mapButton != null) mapButton.setMessage(mapText());
        });
        loadButton = addRenderableWidget(Button.builder(text("bluemap_minimap.setup.load"), button -> loadMaps())
                .bounds(left, 137, halfWidth, 20).build());
        loadButton.active = !loading;
        mapButton = addRenderableWidget(Button.builder(mapText(), button -> {
            int current = maps.indexOf(selectedMap);
            selectedMap = current + 1 < maps.size() ? maps.get(current + 1) : "";
            button.setMessage(mapText());
        }).bounds(left + halfWidth + 4, 137, halfWidth, 20).build());
        addRenderableWidget(Button.builder(discoveryText(), button -> {
            discover = !discover;
            button.setMessage(discoveryText());
        }).bounds(left, 159, panelWidth, 20).build());
        addRenderableWidget(Button.builder(text("bluemap_minimap.cancel"), button -> onClose())
                .bounds(left, 185, halfWidth, 20).build());
        addRenderableWidget(Button.builder(text("bluemap_minimap.setup.apply"), button -> {
            try {
                if (!address.isBlank() || !url.isBlank()) {
                    ServerProfileEditor.apply(draft, address, url, dimension, selectedMap);
                }
                draft.autoDiscoverBlueMap = discover;
                runtime.previewConfig(draft);
                onClose();
            } catch (IllegalArgumentException ex) {
                message = "bluemap_minimap.setup.invalid";
            }
        }).bounds(left + halfWidth + 4, 185, halfWidth, 20).build());
        setInitialFocus(address.isBlank() ? server : endpoint);
    }

    private EditBox field(int x, int y, int fieldWidth, int maxLength, String label, String value) {
        EditBox input = new EditBox(font, x, y, fieldWidth, 18, text(label));
        input.setMaxLength(maxLength);
        input.setValue(value);
        return addRenderableWidget(input);
    }

    private void loadMaps() {
        if (loading) return;
        try {
            var pending = runtime.inspectEndpoint(url);
            int expected = ++requestGeneration;
            loading = true;
            loadButton.active = false;
            message = "bluemap_minimap.setup.loading";
            pending.whenComplete((catalog, error) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (minecraft.gui.screen() != this) return;
                loadButton.active = true;
                if (expected != requestGeneration) {
                    message = "bluemap_minimap.setup.hint";
                    return;
                }
                if (error != null) {
                    message = "bluemap_minimap.setup.failed";
                    return;
                }
                maps = catalog.maps();
                mapButton.setMessage(mapText());
                message = "bluemap_minimap.setup.loaded";
            }));
        } catch (IllegalArgumentException ex) {
            message = "bluemap_minimap.setup.invalid";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.text(font, text("bluemap_minimap.setup.server"), left, 36, 0xFFFFFFFF);
        graphics.text(font, text("bluemap_minimap.setup.url"), left, 69, 0xFFFFFFFF);
        graphics.text(font, text("bluemap_minimap.setup.dimension"), left, 102, 0xFFFFFFFF);
        graphics.centeredText(font, text(message), width / 2, 214, 0xFFB7C3D0);
    }

    @Override
    public void onClose() {
        requestGeneration++;
        minecraft.gui.setScreen(parent);
    }

    private Component mapText() {
        return text("bluemap_minimap.setup.map", selectedMap.isBlank()
                ? text("bluemap_minimap.setup.automatic") : selectedMap);
    }

    private Component discoveryText() {
        return text("bluemap_minimap.setup.discovery", text(discover
                ? "bluemap_minimap.value.enabled" : "bluemap_minimap.value.disabled"));
    }

    private static Component text(String key, Object... arguments) {
        Minecraft client = Minecraft.getInstance();
        String language = client == null ? "en_us" : client.getLanguageManager().getSelected();
        return UiTranslations.component(language, key, arguments);
    }
}

package dev.bluemapminimap.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.bluemapminimap.client.BlueMapMinimapClient;
import net.minecraft.client.gui.screens.Screen;

/** Loaded through the optional modmenu entrypoint only when Mod Menu is installed. */
public final class BlueMapMinimapModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
        return BlueMapMinimapClient::createSettingsScreen;
    }
}

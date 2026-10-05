package dev.bluemapminimap.client;

import dev.bluemapminimap.client.render.MinimapHudRenderer;
import dev.bluemapminimap.client.screen.MinimapSettingsScreen;
import dev.bluemapminimap.model.ClientObservation;
import dev.bluemapminimap.config.MoyoServerPolicy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class BlueMapMinimapClient implements ClientModInitializer {
    private static BlueMapRuntime runtime;
    private static MinimapHudRenderer renderer;
    private static ClientLevel observedLevel;

    @Override
    public void onInitializeClient() {
        Minecraft minecraft = Minecraft.getInstance();
        runtime = new BlueMapRuntime(minecraft, FabricLoader.getInstance().getConfigDir());
        renderer = new MinimapHudRenderer(minecraft);
    }

    public static void onClientTick(Minecraft minecraft) {
        if (runtime == null) return;
        if (observedLevel != minecraft.level) {
            observedLevel = minecraft.level;
            runtime.disconnect();
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getCurrentServer() == null) {
            runtime.tick(null);
            return;
        }
        runtime.tick(new ClientObservation(
                minecraft.getCurrentServer().ip,
                minecraft.level.dimension().identifier().toString(),
                minecraft.player.getX(),
                minecraft.player.getZ(),
                minecraft.player.getYRot()
        ));
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (observedLevel != minecraft.level || minecraft.getCurrentServer() == null
                || !MoyoServerPolicy.supports(minecraft.getCurrentServer().ip)) return;
        if (runtime != null && renderer != null) renderer.render(graphics, runtime.view());
    }

    public static void openSettings(Screen parent) {
        if (runtime == null) return;
        Minecraft.getInstance().gui.setScreen(new MinimapSettingsScreen(parent, runtime));
    }

    public static ConnectionStatus status() {
        return runtime == null ? ConnectionStatus.inactive() : runtime.status();
    }

    public static void shutdown() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }
}

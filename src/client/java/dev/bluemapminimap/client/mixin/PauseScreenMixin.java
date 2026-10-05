package dev.bluemapminimap.client.mixin;

import dev.bluemapminimap.client.BlueMapMinimapClient;
import dev.bluemapminimap.i18n.UiTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bluemapMinimap$addSettingsButton(CallbackInfo ci) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        addRenderableWidget(Button.builder(UiTranslations.component(language, "bluemap_minimap.settings"),
                button -> BlueMapMinimapClient.openSettings(this)).bounds(width - 128, 8, 120, 20).build());
    }
}

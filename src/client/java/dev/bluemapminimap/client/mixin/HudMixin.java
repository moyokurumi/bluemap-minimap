package dev.bluemapminimap.client.mixin;

import dev.bluemapminimap.client.BlueMapMinimapClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow private boolean isHidden;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bluemapMinimap$render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!isHidden) BlueMapMinimapClient.render(graphics);
    }
}

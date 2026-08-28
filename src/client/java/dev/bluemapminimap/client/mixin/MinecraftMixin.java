package dev.bluemapminimap.client.mixin;

import dev.bluemapminimap.client.BlueMapMinimapClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void bluemapMinimap$tick(CallbackInfo ci) {
        BlueMapMinimapClient.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void bluemapMinimap$close(CallbackInfo ci) {
        BlueMapMinimapClient.shutdown();
    }
}

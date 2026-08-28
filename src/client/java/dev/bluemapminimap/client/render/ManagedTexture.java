package dev.bluemapminimap.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public record ManagedTexture(Identifier id, DynamicTexture texture, int width, int height) {
    public void release(Minecraft minecraft) {
        minecraft.getTextureManager().release(id);
    }
}

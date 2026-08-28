package dev.bluemapminimap.client.render;

import dev.bluemapminimap.client.RuntimeView;
import dev.bluemapminimap.config.HudPosition;
import dev.bluemapminimap.config.MinimapConfig;
import dev.bluemapminimap.model.MapDescriptor;
import dev.bluemapminimap.model.RemotePlayer;
import dev.bluemapminimap.model.TileAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Map;

public final class MinimapHudRenderer {
    private static final int MARGIN = 8;
    private static final int BORDER = 2;

    private final Minecraft minecraft;

    public MinimapHudRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(GuiGraphicsExtractor graphics, RuntimeView view) {
        MinimapConfig config = view.config();
        MapDescriptor map = view.map();
        if (!config.enabled || map == null || minecraft.player == null) return;

        int size = config.size;
        int x = horizontal(config.position, graphics.guiWidth(), size);
        int y = vertical(config.position, graphics.guiHeight(), size);
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        double blocksPerPixel = config.zoom;

        graphics.fill(x - BORDER, y - BORDER, x + size + BORDER, y + size + BORDER, 0xCC101418);
        graphics.fill(x, y, x + size, y + size, 0xFF20252B);
        graphics.enableScissor(x, y, x + size, y + size);
        drawTiles(graphics, view, x, y, size, centerX, centerY, blocksPerPixel);
        if (config.showPlayers) drawPlayers(graphics, view, x, y, size, centerX, centerY, blocksPerPixel);
        drawLocalArrow(graphics, centerX, centerY, view.playerYaw());
        graphics.disableScissor();
        graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFE5E7EB);
        graphics.text(minecraft.font, "N", centerX - minecraft.font.width("N") / 2, y + 3, 0xFFFFFFFF, true);
    }

    private void drawTiles(GuiGraphicsExtractor graphics, RuntimeView view, int x, int y, int size,
                           int centerX, int centerY, double blocksPerPixel) {
        MapDescriptor map = view.map();
        for (Map.Entry<TileAddress, ManagedTexture> entry : view.tiles().entrySet()) {
            TileAddress tile = entry.getKey();
            if (!tile.mapId().equals(map.mapId()) || tile.lod() != 1) continue;
            ManagedTexture texture = entry.getValue();
            double worldOriginX = (double) tile.x() * map.tileSizeX();
            double worldOriginZ = (double) tile.z() * map.tileSizeZ();
            int screenX = (int) Math.floor(centerX + (worldOriginX - view.playerX()) / blocksPerPixel);
            int screenY = (int) Math.floor(centerY + (worldOriginZ - view.playerZ()) / blocksPerPixel);
            int width = Math.max(1, (int) Math.ceil(map.tileSizeX() / blocksPerPixel));
            int height = Math.max(1, (int) Math.ceil(map.tileSizeZ() / blocksPerPixel));
            if (screenX >= x + size || screenY >= y + size || screenX + width <= x || screenY + height <= y) continue;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), screenX, screenY, 0, 0, width, height,
                    map.tileSizeX(), map.tileSizeZ(), texture.width(), texture.height());
        }
    }

    private void drawPlayers(GuiGraphicsExtractor graphics, RuntimeView view, int x, int y, int size,
                             int centerX, int centerY, double blocksPerPixel) {
        float headScale = Math.max(0.65F, Math.min(1.0F, size / 160.0F));
        float nameScale = Math.max(0.45F, Math.min(1.0F, size / 176.0F));
        int headSize = Math.max(7, Math.round(10.0F * headScale));
        int halfHead = headSize / 2;
        int edgeMargin = halfHead + 1;
        for (RemotePlayer player : view.players()) {
            if (player.foreign() || player.uuid().equals(view.localPlayerUuid())) continue;
            int px = (int) Math.round(centerX + (player.x() - view.playerX()) / blocksPerPixel);
            int py = (int) Math.round(centerY + (player.z() - view.playerZ()) / blocksPerPixel);
            if (px < x + edgeMargin || py < y + edgeMargin
                    || px >= x + size - edgeMargin || py >= y + size - edgeMargin) continue;
            ManagedTexture head = view.heads().get(player.uuid());
            if (head != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, head.id(), px - halfHead, py - halfHead, 0, 0,
                        headSize, headSize,
                        head.width(), head.height(), head.width(), head.height());
            } else {
                graphics.fill(px - halfHead + 1, py - halfHead + 1,
                        px + halfHead, py + halfHead, 0xFF4DD0E1);
                graphics.outline(px - halfHead, py - halfHead, headSize, headSize, 0xFF101418);
            }
            if (view.config().showNames) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(px, py + halfHead + 2);
                graphics.pose().scale(nameScale, nameScale);
                graphics.text(minecraft.font, player.name(), -minecraft.font.width(player.name()) / 2,
                        0, 0xFFFFFFFF, true);
                graphics.pose().popMatrix();
            }
        }
    }

    private static void drawLocalArrow(GuiGraphicsExtractor graphics, int centerX, int centerY, float yaw) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(180.0F - yaw));
        graphics.fill(-2, -7, 2, 4, 0xFF0B1116);
        graphics.fill(-5, -4, 5, -1, 0xFF0B1116);
        graphics.fill(-1, -6, 1, 3, 0xFFFFFFFF);
        graphics.fill(-4, -3, 4, -2, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    private static int horizontal(HudPosition position, int screenWidth, int size) {
        return switch (position) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - size - MARGIN;
        };
    }

    private static int vertical(HudPosition position, int screenHeight, int size) {
        return switch (position) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - size - MARGIN;
        };
    }
}

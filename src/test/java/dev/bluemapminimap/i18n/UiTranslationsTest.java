package dev.bluemapminimap.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTranslationsTest {
    @Test
    void japaneseSettingsLabelsNeverExposeTranslationKeys() {
        assertEquals("BlueMap Minimap 設定",
                UiTranslations.format("ja_jp", "bluemap_minimap.settings"));
        assertEquals("地図サイズ: 80 px ▶",
                UiTranslations.format("ja_jp", "bluemap_minimap.size", 80));
        assertEquals("プレイヤーアイコン: 表示",
                UiTranslations.format("ja_jp", "bluemap_minimap.players", "表示"));
        assertFalse(UiTranslations.format("ja_jp", "bluemap_minimap.tooltip.zoom")
                .contains("bluemap_minimap"));
    }

    @Test
    void englishRemainsAvailableWithoutFabricApi() {
        assertEquals("BlueMap Minimap",
                UiTranslations.format("en_us", "bluemap_minimap.settings"));
        assertEquals("Map size: 112 px ▶",
                UiTranslations.format("en_us", "bluemap_minimap.size", 112));
    }

    @Test
    void everyScreenKeyUsedByTheUiHasABuiltInTranslation() {
        assertTrue(UiTranslations.contains("bluemap_minimap.current"));
        assertTrue(UiTranslations.contains("bluemap_minimap.status"));
        assertTrue(UiTranslations.contains("bluemap_minimap.tooltip.done"));
    }
}

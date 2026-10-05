package dev.bluemapminimap.i18n;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Small built-in UI catalogue. BlueMap Minimap intentionally has no Fabric API
 * dependency, so it cannot rely on Fabric's mod resource-pack injection for its
 * language files. Keeping the settings UI strings here guarantees that the UI
 * remains readable in a loader-only installation.
 */
public final class UiTranslations {
    private static final Map<String, Translation> TEXT = Map.ofEntries(
            entry("bluemap_minimap.settings", "BlueMap Minimap", "BlueMap Minimap 設定"),
            entry("bluemap_minimap.enabled", "Minimap: %s", "ミニマップ: %s"),
            entry("bluemap_minimap.size", "Map size: %s px ▶", "地図サイズ: %s px ▶"),
            entry("bluemap_minimap.position", "Screen position: %s ▶", "画面上の位置: %s ▶"),
            entry("bluemap_minimap.zoom", "Visible area: %s (%s blocks/px) ▶", "表示範囲: %s（%s ブロック/px）▶"),
            entry("bluemap_minimap.players", "Player icons: %s", "プレイヤーアイコン: %s"),
            entry("bluemap_minimap.names", "Map player names: %s", "地図内の名前: %s"),
            entry("bluemap_minimap.orientation", "Map rotation: %s ▶", "地図の回転: %s ▶"),
            entry("bluemap_minimap.north_up", "North up", "北を上に固定"),
            entry("bluemap_minimap.heading_up", "Heading up", "進行方向を上に固定"),
            entry("bluemap_minimap.compass", "North (N): %s", "北（N）: %s"),
            entry("bluemap_minimap.coordinates", "Current coordinates: %s", "現在座標: %s"),
            entry("bluemap_minimap.value.enabled", "On", "オン"),
            entry("bluemap_minimap.value.disabled", "Off", "オフ"),
            entry("bluemap_minimap.value.visible", "Shown", "表示"),
            entry("bluemap_minimap.value.hidden", "Hidden", "非表示"),
            entry("bluemap_minimap.zoom.near", "Close detail", "近くを詳細に"),
            entry("bluemap_minimap.zoom.standard", "Standard", "標準"),
            entry("bluemap_minimap.zoom.wide", "Wide area", "広い範囲"),
            entry("bluemap_minimap.done", "Done (save)", "完了（保存）"),
            entry("bluemap_minimap.cancel", "Cancel", "キャンセル"),
            entry("bluemap_minimap.top_right", "Top right", "右上"),
            entry("bluemap_minimap.top_left", "Top left", "左上"),
            entry("bluemap_minimap.bottom_right", "Bottom right", "右下"),
            entry("bluemap_minimap.bottom_left", "Bottom left", "左下"),
            entry("bluemap_minimap.config_hint", "Click a button to change it / Done saves your settings",
                    "ボタンをクリックして変更 ／ 完了で保存"),
            entry("bluemap_minimap.current", "Current: size %s px / position %s / orientation %s",
                    "現在: サイズ %s px ／ 位置 %s ／ 向き %s"),
            entry("bluemap_minimap.status", "Status: %s | map %s | tile %s | queue %s | cache %s",
                    "状態: %s | map %s | tile %s | queue %s | cache %s"),
            entry("bluemap_minimap.tooltip.enabled", "Turn the entire minimap display on or off.",
                    "ミニマップ全体の表示をオン／オフします。"),
            entry("bluemap_minimap.tooltip.size", "Cycle through seven minimap width and height sizes.",
                    "ミニマップの縦横サイズを7段階で切り替えます。"),
            entry("bluemap_minimap.tooltip.zoom",
                    "Change how many blocks each pixel represents. A larger value shows a wider area.",
                    "1ピクセルに表示するブロック数を変更します。値が大きいほど広い範囲を表示します。"),
            entry("bluemap_minimap.tooltip.position", "Choose the screen corner where the minimap is displayed.",
                    "ミニマップを表示する画面の角を切り替えます。"),
            entry("bluemap_minimap.tooltip.orientation",
                    "Keep north at the top or rotate the map to keep your heading at the top.",
                    "北を上に固定するか、進行方向に合わせて地図を回転するかを切り替えます。"),
            entry("bluemap_minimap.tooltip.players",
                    "Show online BlueMap players as head icons. Mobs and minecarts are never included.",
                    "BlueMapのオンラインプレイヤーを頭アイコンで地図内に表示します。Mobやトロッコは表示しません。"),
            entry("bluemap_minimap.tooltip.names", "Show player names beside their icons inside the minimap.",
                    "地図内のプレイヤーアイコンに名前を表示します。"),
            entry("bluemap_minimap.tooltip.compass", "Show an N marker for north inside the minimap.",
                    "地図内に北方向を示すNを表示します。"),
            entry("bluemap_minimap.tooltip.coordinates", "Show your current coordinates below the minimap.",
                    "ミニマップの下に自分の現在座標を表示します。"),
            entry("bluemap_minimap.tooltip.cancel",
                    "Discard changes made on this screen and restore the saved settings.",
                    "この画面で行った変更を破棄し、保存済み設定へ戻します。"),
            entry("bluemap_minimap.tooltip.done", "Save the current settings and return to the game.",
                    "現在の設定を保存してゲームへ戻ります。")
    );

    private UiTranslations() {
    }

    public static Component component(String languageCode, String key, Object... arguments) {
        return Component.literal(format(languageCode, key, arguments));
    }

    public static String format(String languageCode, String key, Object... arguments) {
        Translation translation = TEXT.get(key);
        if (translation == null) return key;
        String template = isJapanese(languageCode) ? translation.japanese() : translation.english();
        Object[] printable = Arrays.stream(arguments)
                .map(argument -> argument instanceof Component component ? component.getString() : argument)
                .toArray();
        return String.format(Locale.ROOT, template, printable);
    }

    public static boolean contains(String key) {
        return TEXT.containsKey(key);
    }

    private static boolean isJapanese(String languageCode) {
        return languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("ja_");
    }

    private static Map.Entry<String, Translation> entry(String key, String english, String japanese) {
        return Map.entry(key, new Translation(english, japanese));
    }

    private record Translation(String english, String japanese) {
    }
}

# BlueMap Minimap のダウンロード・導入（RC.1）

Minecraftの画面の隅に、サーバーのBlueMap地図と公開中のプレイヤーを表示するFabric用MODです。
導入は任意です。BlueMap公式のMODではありません。

## ダウンロード

現在ダウンロードできるのは、**旧試用版 0.1.0-rc.1** です。
RC.3候補は自動テスト107件を通過していますが、実クライアント確認とRelease公開は未完了です。
[RC.3の検証状況](release-notes/v0.1.0-rc.3.md)を参照してください。

- [Releaseページ](https://github.com/moyokurumi/bluemap-minimap/releases/tag/v0.1.0-rc.1)
- [導入用JARをダウンロード](https://github.com/moyokurumi/bluemap-minimap/releases/download/v0.1.0-rc.1/bluemap-minimap-0.1.0-rc.1.jar)

Releaseの **Assets → `bluemap-minimap-0.1.0-rc.1.jar`** を選んでもダウンロードできます。
`-sources.jar` と `Source code (zip / tar.gz)` はソースコード用なので、`mods`には入れません。

対応環境：**Minecraft Java Edition 26.2 / Java 25 / Fabric Loader 0.19.3以上（0.19系）**。
Minecraft 26.3向けの配布物ではありません。Fabric API、Xaero、Map Link、Mod Menuは不要です。

## 導入手順

1. ランチャーでMinecraft 26.2のFabric環境を用意します。
2. 使用中のゲームディレクトリの `mods` フォルダーへ、導入用JARを入れます。
3. 一度起動してからMinecraftを終了します。
4. 生成された `config/bluemap-minimap.json` に、接続先とBlueMapの設定を追加します。
5. Minecraftを起動してサーバーへ接続します。
6. 表示位置・大きさ・ズームなどは **Esc → BlueMap Minimap Settings** で変更できます。

**RC.1は接続先の手動設定が必要です。** BlueMap URLの自動検出や地図の回転には対応していません。
開発中の版の説明と混同しないよう、このページは公開済みRC.1の機能に合わせています。

設定例（アドレスとmap IDは参加先の案内に合わせて置き換えてください）：

```json
{
  "enabled": true,
  "size": 80,
  "position": "TOP_RIGHT",
  "zoom": 2.0,
  "showPlayers": true,
  "showNames": true,
  "servers": {
    "play.example.com": {
      "blueMapUrl": "https://map.example.com/",
      "dimensions": {
        "minecraft:overworld": "overworld"
      }
    }
  }
}
```

`servers` のキーには、マルチプレイ一覧で使うサーバーアドレスを指定します。
既にほかのサーバーを設定している場合は、その設定を残して項目を追加してください。
BlueMapのURLやmap IDが分からないときは、参加先の運営案内を確認してください。

## 表示されるもの・制限

- 北が上の地形地図、自分の向き、同じ地図内で公開されているプレイヤーの顔と名前を表示します。
- 建築などの変更は、BlueMapが地図を更新したあとに反映されます。
- BlueMapがない場所や、対応を設定していないディメンションでは地図が表示されません。
- ピン、洞窟、Mob、死亡地点、全画面マップの表示はありません。

## 配布ファイルの確認

2026-10-04に既存Releaseからダウンロードし、GitHub APIのdigestと一致を確認しました。

| ファイル | bytes | SHA-256 |
| --- | ---: | --- |
| `bluemap-minimap-0.1.0-rc.1.jar` | 87252 | `79ab9349d67199b91c7306488849be953c77939f264d9848037e4875873e5af5` |
| `bluemap-minimap-0.1.0-rc.1-sources.jar` | 35717 | `ff02e317d82144d51a50f682bf79d79aa573f8a5bf0bfabff3ebd1abe8ff6b71` |

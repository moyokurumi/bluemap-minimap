# BlueMap Minimap のダウンロード・導入（RC.1）

> **もよさば専用のクライアントMODです。一般のBlueMap導入サーバーでは使用できません。**
> もよさばの参加者向け配布です。Fabricクライアントの `mods` に入れるMODで、
> サーバーに入れるプラグインではありません。他サーバーは対応対象外です。

Minecraftの画面の隅に、サーバーのBlueMap地図と公開中のプレイヤーを表示するFabric用MODです。
導入は任意です。BlueMap公式のMODではありません。

## ダウンロード

現在ダウンロードできるのは、**旧試用版 0.1.0-rc.1** です。
DiscordではRC.3が配布されていますが、GitHub Releaseへ公開済みなのはRC.1です。
JSON編集を不要にするモヨサバ専用RC.4は配布準備中です。
[RC.4の導入案内・未公開の注意](download-moyo-ja.md)を参照してください。

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

もよさば用の設定例：

```json
{
  "enabled": true,
  "size": 80,
  "position": "TOP_RIGHT",
  "zoom": 2.0,
  "showPlayers": true,
  "showNames": true,
  "servers": {
    "mc.moyokurumi.com": {
      "blueMapUrl": "https://mcmap.moyokurumi.com/",
      "dimensions": {
        "minecraft:overworld": "overworld"
      }
    }
  }
}
```

マルチプレイ一覧の接続先は **`mc.moyokurumi.com`** を使ってください。
既にほかのサーバーを設定している場合は、その設定を残して項目を追加してください。
地図が表示されない場合は、もよさばの運営へ相談してください。

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

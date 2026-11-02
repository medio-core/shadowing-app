# Play Console 用グラフィック

## アプリアイコン（必須）

| 項目 | 仕様（Google Play） |
|------|---------------------|
| ファイル | `icon-512.png` |
| サイズ | **512 × 512 px**（正方形） |
| 形式 | **PNG** または **JPEG** |
| 容量 | **1 MB 以下** |
| ポリシー | [デザイン仕様とメタデータ](https://support.google.com/googleplay/android-developer/answer/9866151) に準拠 |

再生成: `debug/create_play_store_icon.ps1` を実行。

Play Console → **ストアの掲載情報** → **グラフィック** → **アプリアイコン** にアップロード。

端末のホーム画面用アイコンは APK 内の `@mipmap/ic_launcher`（別物）です。
ストア用 PNG を変えても、端末アイコンは新しい AAB を出すまで変わりません。

## フィーチャーグラフィック（推奨）

| 項目 | 仕様（Google Play） |
|------|---------------------|
| ファイル | `feature-graphic.png` |
| サイズ | **1024 × 500 px** |
| 形式 | **PNG** または **JPEG** |
| 容量 | **15 MB 以下** |

Play Console → **ストアの掲載情報** → **グラフィック** → **フィーチャー グラフィック** にアップロード。

再生成: `python debug/create_feature_graphic.py`

## その他（任意だが推奨）

| 素材 | サイズ | 用途 |
|------|--------|------|
| スマホスクショ | 2～8 枚・9:16 推奨 | [SCREENSHOTS.md](SCREENSHOTS.md) 参照 |

## アイコンを差し替えるとき

1. `icon-512.png` を差し替え → Play に再アップロード
2. 端末アイコンも揃える → `ic_launcher_foreground.xml` 等を編集して AAB を再ビルド

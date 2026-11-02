# Play Console スクリーンショット

## 要件（スマートフォン）

| 項目 | 内容 |
|------|------|
| 枚数 | **2～8 枚**（最低 2） |
| 形式 | PNG または JPEG |
| 容量 | 各 **8 MB 以下** |
| 比率 | **16:9** または **9:16** |
| 辺の長さ | 各辺 **320～3,840 px** |

タブレット用は別枠。まず **スマートフォン** だけで可。

## おすすめの撮り方（実機 or エミュレータ）

1. Android Studio で **Run**（実機推奨）
2. 次の画面を **2枚以上** 撮る（実際の見た目が Play ポリシー的に安全）:

| 優先 | 画面 | 撮る内容 |
|------|------|----------|
| 1 | メイン | 動画＋英語字幕＋操作ボタンが見える状態 |
| 2 | 字幕スワイプ | 翻訳表示 or 英語表示が分かる状態 |
| 3 | タイミング調整 | パネルを開いた状態（任意） |
| 4 | 設定 | 設定一覧（任意） |

### 実機

- **電源 + 音量下** 長押し（機種により異なる）
- または **最近使ったアプリ** → スクショのサムネイル

### エミュレータ

- ツールバーの **カメラ（Screenshot）** アイコン
- 端末: **Pixel 6** など縦長（9:16 に近い）

### adb（USB デバッグ時）

```powershell
adb shell screencap -p /sdcard/ss.png
adb pull /sdcard/ss.png docs\play-store\screenshots\raw-01.png
```

## アップロード前の確認

```powershell
cd C:\Users\Me\Desktop\shadowing-app\debug
python prepare_play_screenshots.py check ..\docs\play-store\screenshots\*.png
```

比率やサイズが合わないとき（例: 正方形に近いキャプチャ）:

```powershell
python prepare_play_screenshots.py fix ..\docs\play-store\screenshots\raw-01.png ..\docs\play-store\screenshots\phone-01.png
```

`phone-01.png` / `phone-02.png` を Play Console にアップロード。

加工（動画領域だけ差し替え・元の解像度のまま）:

```powershell
python debug/compose_store_screenshots.py
```

出力: `phone-01-english.png`, `phone-02-translation.png`（460×1024 など元と同じサイズ）

Play 用に 9:16 へ拡大する場合（全体クロップしない・余白付き）:

```powershell
python prepare_play_screenshots.py pad phone-01-english.png phone-01-play.png
```

## プロモーション対象（任意だが狙う場合）

Google の「プロモーションの対象」になるには:

| 条件 | 内容 |
|------|------|
| 枚数 | **4 枚以上** |
| 解像度 | **幅 ≥ 1080** かつ **高さ ≥ 1080**（各枚） |
| 比率 | 9:16 または 16:9（`pad` 後は 1080×1920 で OK） |

### 手順

1. 加工済みスクショを 4 枚用意（`phone-01` … `phone-04`）
2. 一括変換:

```powershell
cd debug
python prepare_promo_screenshots.py
```

3. `docs/play-store/screenshots/promo/promo-01.png` … を Play にアップロード

### 推奨 4 画面

| # | ファイル名の例 | 画面 |
|---|----------------|------|
| 1 | `phone-01-english.png` | メイン・英語字幕（済） |
| 2 | `phone-02-translation.png` | 翻訳表示（済） |
| 3 | `phone-03-timing.png` | タイミング調整を開いた状態 |
| 4 | `phone-04-settings.png` | 設定画面 |

3・4 は実機/エミュで撮影 → 映画映像なら `compose_store_screenshots.py` で動画だけ差し替え → 上記スクリプト。

### きれいに撮る（推奨）

Android Studio エミュ **Pixel 6**（1080×2400）で Run → スクショすると、拡大が少なくプロモ向き。  
取った PNG を `phone-03-timing.png` などとして `screenshots/` に保存。

## Play Console での操作

**ストアの掲載情報** → **グラフィック** → **スマートフォンのスクリーンショット** → 2枚以上追加。

## 注意

- ストア用に **存在しない機能** を写さない
- 個人情報・著作権で問題ある動画・字幕は写さない
- モック画像のみの提出は避け、**実アプリの画面** を使う

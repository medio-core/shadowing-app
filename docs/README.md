# 法的情報ページ

アプリ固有の法的文面を管理・確認するための静的 HTML です。
アプリ内の文面（`LegalDocumentTexts.kt`）と内容を揃えます。

## Play Console / AdMob に登録する URL

SKG Labs 公式サイトの「プライバシーポリシー」節を公開し、次の URL を使います。

```
https://skg-labs.com/
```

## 公開確認

1. `https://skg-labs.com/` をブラウザで開く
2. 「プライバシーポリシー」節が表示されることを確認する
3. Play Console と AdMob のプライバシーポリシー欄に上記 URL を登録する

## ファイル

| ファイル | 用途 |
|----------|------|
| `privacy-policy.html` | Play 必須のプライバシーポリシー |
| `terms-of-service.html` | 利用規約（任意・ストア説明からリンク可） |
| `index.html` | 一覧ページ |

## 更新するとき

1. `docs/*.html` と `app/.../LegalDocumentTexts.kt` を同じ内容に揃える
2. `app/src/main/res/values/strings.xml` の `privacy_policy_last_updated` を更新
3. SKG Labs 公式サイトの公開ポリシーも同じ変更内容へ更新する

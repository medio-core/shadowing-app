# 法的情報ページ（GitHub Pages 用）

Play Console の「プライバシーポリシー URL」に登録する静的 HTML です。
アプリ内の文面（`LegalDocumentTexts.kt`）と内容を揃えています。

## Play Console に登録する URL

GitHub Pages を有効にしたあと、次の URL を使います。

```
https://medio-core.github.io/shadowing-app/privacy-policy.html
```

例（別アカウントの場合）:

```
https://<GitHubユーザー名>.github.io/<リポジトリ名>/privacy-policy.html
```

## 公開手順（GitHub Pages）

1. このプロジェクトを GitHub にプッシュ（`docs/` フォルダごと）
2. GitHub のリポジトリ → **Settings** → **Pages**
3. **Build and deployment** → Source: **Deploy from a branch**
4. Branch: `main`（または `master`）、Folder: **`/docs`**
5. **Save** → 数分待つと URL が表示される
6. ブラウザで `privacy-policy.html` が開けることを確認
7. Play Console → **ストアの掲載情報** → **プライバシーポリシー** に上記 URL を貼る

## ファイル

| ファイル | 用途 |
|----------|------|
| `privacy-policy.html` | Play 必須のプライバシーポリシー |
| `terms-of-service.html` | 利用規約（任意・ストア説明からリンク可） |
| `index.html` | 一覧ページ |

## 更新するとき

1. `docs/*.html` と `app/.../LegalDocumentTexts.kt` を同じ内容に揃える
2. `app/src/main/res/values/strings.xml` の `legal_last_updated` を更新
3. GitHub に push（Pages は自動で再デプロイ）

# CLAUDE.md — photo-management

DSLR で撮った写真を整理する個人用 Web アプリ。将来的にポートフォリオ公開と AWS S3 移行を見据える。

## 構成

```
photo-management/
├── backend/     Spring Boot 3.5 / Java 21 / MyBatis / Flyway / PostgreSQL
├── frontend/    Next.js 16 (App Router) / TypeScript / Tailwind / Vitest
├── docker-compose.yml   開発用 PostgreSQL
├── lefthook.yml         pre-commit フック
└── Makefile             全コマンドのエントリポイント
```

## コマンド

すべて Makefile に集約。**個別のツールを直接叩く必要はない。**

| コマンド | 用途 |
| --- | --- |
| `make setup` | 初回セットアップ |
| `make dev` | backend + frontend 並列起動 |
| `make check` | CI と同等のチェック（lint + test、ローカルで完結） |
| `make format` | 自動整形 |
| `make arch` | ArchUnit のみ実行 |
| `make coverage` | JaCoCo HTML レポート生成 |
| `make security` | OWASP Dependency Check |
| `make outdated` | 古い依存検出 |
| `make help` | 全コマンド一覧 |

## やってはいけないこと

- 適用済み Flyway migration ファイルの編集（追加のみ可）。
- `main` への直接 push / force push（ブランチ保護で禁止）。
- 写真本体を DB に保存（必ずファイルシステム or S3）。
- `Hibernate ddl-auto=update` 等のスキーマ自動変更。
- 認証付きエンドポイントを `permitAll()` で素通し（M2 以降）。

## ドキュメント索引

- **詳細な作業はそれぞれのサブディレクトリへ**
  - `backend/CLAUDE.md` — バックエンド規約（パッケージ構造、3-layer、命名、エラー形）
  - `frontend/CLAUDE.md` — フロントエンドの最小ガイド
- **設計の Why は** `docs/adr/`（Architecture Decision Records）
- **アーキ全体像は** `docs/architecture.md`
- **「〜を追加する」手順は** `docs/recipes/`
- **要件定義の詳細は** `/Users/hiroya/.claude/plans/curried-sprouting-zebra.md`

## CI / ブランチ保護のメモ

- `backend.yml` と `frontend.yml` が必須コンテキスト（job 名: `backend` / `frontend`）。両方緑でないと merge 不可。
- 詳細: `docs/architecture.md#ci`

# CLAUDE.md — photo-management

このファイルは Claude Code および新規開発者向けのプロジェクト規約書。
要件定義の詳細は `/Users/hiroya/.claude/plans/curried-sprouting-zebra.md` を参照。

---

## プロジェクト概要

- **目的**: DSLR で撮影した写真を整理・検索・閲覧する個人用 Web アプリ
- **将来構想**: アルバム公開によるポートフォリオ化、AWS S3 への移行
- **副次目的**: バックエンドエンジニアとしての技術力向上（認証認可・スケーラビリティ）

---

## 構成

```
photo-management/
├── backend/       # Spring Boot 3.5 / Java 21 / MyBatis / Flyway / PostgreSQL
├── frontend/      # Next.js 16 (App Router) / TypeScript / Tailwind / Vitest
├── docker-compose.yml  # 開発用 PostgreSQL
├── lefthook.yml   # pre-commit フック
└── Makefile       # 共通コマンドのエントリポイント
```

---

## よく使うコマンド

すべて Makefile に集約。**個別のツールコマンドを覚える必要はない。**

| コマンド | 用途 |
| --- | --- |
| `make setup` | 初回セットアップ（依存インストール + Docker起動 + マイグレーション） |
| `make dev` | backend + frontend を並列起動 |
| `make test` | 全テスト |
| `make lint` | 全リンタ（Spotless/Checkstyle/SpotBugs/ESLint/Prettier/typecheck） |
| `make format` | 自動整形（書ける所は機械的に直す） |
| `make check` | CI と同等のチェックをローカル実行 |
| `make db-up` / `make db-down` | PostgreSQL の起動・停止 |

---

## 規約

### コード規約
- **Java**: Google Java Format（Spotless が自動整形）。`@SuppressWarnings` は理由をコメントで明記。
- **TypeScript**: Prettier + ESLint（Next.js 推奨設定 + prettier との競合無効化）。
- **インポート順**: ツールに任せる（Spotless / ESLint の auto-fix）。

### コミット規約
- 1 PR = 1 論理的変更（M1 完了など大きな単位は分割）。
- main への直接 push 禁止（ブランチ保護で強制）。
- pre-commit が format/lint を自動実行する（lefthook）。

### マイグレーション規約（**重要**）
- ファイル名: `V{n}__{snake_case}.sql`（例: `V2__create_users.sql`）。
- **適用済み migration は編集禁止**。修正は新しい `V{n+1}` を追加。
- 破壊的変更は段階的に: ①新カラム追加 → ②アプリ両対応 → ③旧カラム削除。

### ストレージ抽象化
- 写真の保存先は `PhotoStorage` インターフェース経由で扱う（M3 で導入予定）。
- Phase1: `LocalPhotoStorage`、Phase2: `S3PhotoStorage`。`StorageKey` という値オブジェクトで管理。

---

## やってはいけないこと

- 適用済み Flyway migration の編集。
- `main` への直接 push、force push。
- 認証付きエンドポイントを `permitAll()` で素通しする（M2 以降）。
- 写真本体を DB に保存する（必ずファイルシステム or S3 へ）。
- `Hibernate ddl-auto=update` などスキーマ自動変更を有効化する。

---

## アーキテクチャ方針

### バックエンド
- 標準的なレイヤー構成: `controller` → `service` → `mapper` (MyBatis) → DB
- DTO ↔ Domain の変換は明示的に行う（mapper クラス or レコードの static method）
- 例外: ドメイン層は独自例外、Controller で `@RestControllerAdvice` により ProblemDetails (RFC 9457) に変換
- 設定は `application.yml`、環境変数で上書き可能

### フロントエンド
- App Router（`src/app/`）
- API 呼び出しは Server Components または Server Actions を優先
- 認証情報は HTTP-only Cookie で受け渡し（M2 以降）

---

## レシピ

### 新しい API を追加する
1. `backend/src/main/java/com/example/photomanagement/{feature}/` に Controller / Service / Mapper を追加
2. Mapper XML が必要なら `backend/src/main/resources/com/example/photomanagement/{feature}/` に配置
3. 統合テストを `backend/src/test/java/.../{feature}/` に追加（Testcontainers ベース）
4. `make test-backend` が緑

### DB スキーマを変更する
1. `backend/src/main/resources/db/migration/V{n+1}__xxx.sql` を新規作成
2. 既存の Migration ファイルは絶対に編集しない
3. `make test-backend` でマイグレーション統合テストが緑

### フロントエンドの新規ページを追加する
1. `frontend/src/app/{route}/page.tsx` を追加
2. テストは `page.test.tsx` を隣に配置
3. `make test-frontend` および `make lint-frontend` が緑

---

## 落とし穴

- **Spring Boot 3.5 + MyBatis 3.0.5**: スターターのバージョン互換は固定で書いてある（build.gradle）。Boot をアップグレードするときは MyBatis 側も追従要。
- **Testcontainers**: Docker が起動していないと backend テストはコケる。`docker info` で確認。
- **pnpm + Node**: Node 22.13+ が必要。`nodenv local` で固定推奨。
- **Next.js 16**: React 19 系。Server Components がデフォルト、`"use client"` 明示が必要。

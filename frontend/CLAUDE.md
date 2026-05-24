# frontend/CLAUDE.md

Next.js 16 (App Router) / TypeScript / Tailwind / Vitest。

## コマンド

ルートの `make dev` / `make lint-frontend` / `make test-frontend` 経由が標準。
個別に動かしたい場合:

| コマンド | 用途 |
| --- | --- |
| `pnpm dev` | 開発サーバ起動 |
| `pnpm build` | 本番ビルド |
| `pnpm typecheck` | TypeScript 型チェック |
| `pnpm lint` / `pnpm lint:fix` | ESLint |
| `pnpm format` / `pnpm format:check` | Prettier |
| `pnpm test` / `pnpm test:watch` | Vitest |

## 構成

- App Router (`src/app/`)
- API 呼び出しは Server Components or Server Actions を優先
- 認証情報は HTTP-only Cookie（M2 以降）
- テストは `*.test.tsx` を実装ファイルの隣に置く（`@/` エイリアスは `src/` を指す）

## 落とし穴

- Next.js 16 / React 19: Server Components がデフォルト。クライアント機能は `"use client"` 明示。
- pnpm の `pnpm-workspace.yaml` で `sharp` / `unrs-resolver` / `esbuild` のビルドスクリプトを許可済み。新たにビルドスクリプトが必要な依存を追加するときは `onlyBuiltDependencies` に足す。

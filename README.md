# photo-management

一眼レフカメラで撮影した写真を管理するための個人用 Web アプリ。
将来的にはアルバムの公開機能を備えたポートフォリオサービスとしての発展を見据える。

## 構成

- **frontend/** — Next.js (App Router) + TypeScript
- **backend/** — Spring Boot (Java 21) + MyBatis + Flyway
- **docker-compose.yml** — 開発用 PostgreSQL

## クイックスタート

```sh
# 初回セットアップ
make setup

# 開発サーバー起動 (backend + frontend)
make dev

# 全テスト
make test

# CI と同じチェックをローカル実行
make check
```

詳細は [CLAUDE.md](./CLAUDE.md) を参照。

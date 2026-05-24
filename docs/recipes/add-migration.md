# DB マイグレーションを追加する

## 手順

1. **次の連番を確認**: `backend/src/main/resources/db/migration/` の `V{n}__xxx.sql` の最大 n を見つける。
2. **新ファイル作成**: `V{n+1}__<snake_case_description>.sql`（例: `V2__create_users.sql`）。
3. **マイグレーション内容を SQL で記述**。PostgreSQL の機能（`JSONB`, GIN index, etc.）はフル活用 OK。
4. **`make migrate`** で適用、または `make test-backend` で Testcontainers 経由の検証。

## 絶対やってはいけないこと

- **既にコミットされたマイグレーションファイルの編集** — Flyway は checksum で検証し、本番で必ずコケる。
- 修正したい場合は **打ち消す新しいマイグレーション** を追加する。
  - 例: V3 でカラム追加を間違えた → V4 で削除 + 修正版で再追加。

## 破壊的変更の段階分割

カラム削除や型変更は 1 リリースに収めず分割する:

| Step | 変更 |
| --- | --- |
| ① | 新カラム追加（NULLABLE）→ アプリは旧カラムを読む |
| ② | アプリを「両方読む / 新側に書く」に変更 → デプロイ |
| ③ | 旧カラム削除 |

## 規約まとめ

- ファイル名: `V{n}__{snake_case_description}.sql`
- DDL は冪等でなくてよい（Flyway がバージョン管理する）
- ロールバックは Community 版にはない → 打ち消しマイグレーションで対応
- 統合テストが自動でマイグレーション → Mapper の整合性を担保する

# architecture.md

photo-management のアーキ全体像。実装の Why は `docs/adr/` を、操作手順は `docs/recipes/` を参照。

## 俯瞰

```
[ Next.js (App Router) ]
        ↓ fetch / Server Actions
[ Spring Boot REST API ]
   - Controller (HTTP)
   - Service    (business / @Transactional)
   - Mapper     (MyBatis SQL)
        ↓ JDBC
[ PostgreSQL ]
        ↑ Flyway migrations
```

## バックエンド構造

- **Package-by-Feature**: 機能ごとに 1 サブパッケージ。詳細は `docs/adr/0002-package-by-feature.md`。
- **シンプル 3-layer**: Controller / Service / Mapper。詳細は `docs/adr/0003-simple-3layer.md`。
- **MyBatis 採用**: JPA を選ばず SQL を明示。詳細は `docs/adr/0001-mybatis-over-jpa.md`。
- **ArchUnit による強制**: レイヤ境界と命名規約を CI で検証。詳細は `docs/adr/0004-archunit-enforcement.md`。

## エラー契約（RFC 9457 ProblemDetails）

- すべての例外を `application/problem+json` に統一。
- 階層: `DomainException`（抽象）→ `NotFoundException` / `ForbiddenException` / `ValidationException` / `ConflictException`。
- 変換: `common/error/ProblemDetailsAdvice`（`@RestControllerAdvice`）。
- 拡張フィールド: `errorCode`（機械可読）、`requestId`（MDC から）。
- Bean Validation 失敗時は `errors[]` に `{field, message}` を含める。

## ロギング・観測性

- `logback-spring.xml` でプロファイル分岐。
  - `prod` プロファイル: `logstash-logback-encoder` による JSON 出力。
  - それ以外: 人間可読の pattern + `requestId` を含む。
- `common/web/RequestIdFilter`:
  - 最上位フィルターとして実行（`Ordered.HIGHEST_PRECEDENCE`）。
  - `X-Request-Id` ヘッダがあれば採用、無ければ UUID 生成。
  - MDC `requestId` キーに格納 → 全ログ行に出力。
  - レスポンスヘッダにもエコー（フロントエンドからサーバーログを辿れる）。
- メトリクス・トレースは今フェーズ未導入。Actuator は health/info のみ公開。

## ストレージ抽象化

- 写真ファイルは DB に保存しない。
- `PhotoStorage` インターフェース（M3 で導入予定）で保存先を抽象化。
  - `LocalPhotoStorage`（Phase 1: 開発・初期運用）
  - `S3PhotoStorage`（Phase 2: 本番）
- 切り替えは `application.yml` のプロパティで行う。
- 値オブジェクト `StorageKey` で保存場所を識別（DB 列は `storage_key`）。

## 認証認可（M2 で実装）

- Spring Security ベース。
- 認証: email + password、BCrypt ハッシュ化。
- セッション: JWT（短命） + Refresh Token（DB 管理、ローテーション、ファミリー検知）。
- 認可: RBAC（ロール: `ADMIN` / `OWNER` / `EDITOR` / `VIEWER`）。
- アルバム単位の権限制御（将来の公開/共有を見据える）。

## DB / マイグレーション

- PostgreSQL。
- Flyway。`backend/src/main/resources/db/migration/V{n}__xxx.sql`。
- **適用済みファイルの編集禁止**。修正は `V{n+1}` を追加。
- 破壊的変更は 3 段階: ①新カラム追加 → ②アプリ両対応 → ③旧カラム削除。
- スキーマ整合性は Testcontainers + マイグレーション統合テストで担保。

## CI <a id="ci"></a>

- GitHub Actions: `.github/workflows/backend.yml` / `frontend.yml`。
- 両ワークフローが全 PR と main push で必ず走る（path filter なし）。
- ジョブ名 = `backend` / `frontend`。これがブランチ保護の必須コンテキスト。
- 別途 `dependency-check.yml` が OWASP Dependency Check を週次 + 手動実行。
- カバレッジ: JaCoCo HTML を artifact アップロード（閾値は設けない）。

## 値オブジェクト方針

- 単なる `Long`/`String` で扱うと混同しやすい型は `record` で包む。
  - 例: `PhotoId(Long value)`, `StorageKey(String value)`, `Rating(int value)`, `Email(String value)`。
- バリデーションは record のコンパクトコンストラクタで行う。
- 詳細は `docs/recipes/add-value-object.md`。

## スケーラビリティ方針

- 初期: 数千枚（個人）→ 目標: 十万枚規模。
- 検索の pagination は **cursor / keyset 方式**を採用予定（offset は使わない）。
- サムネイル: 一覧用 / 詳細用の複数サイズを事前生成（M3）。
- 画像配信は署名 URL or 認証付きエンドポイント経由のみ（直接アクセス禁止）。

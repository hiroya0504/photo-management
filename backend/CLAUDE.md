# backend/CLAUDE.md

Spring Boot 3.5 / Java 21 / MyBatis / Flyway / PostgreSQL。

このファイルは `backend/` 配下で作業中の Claude Code セッションに自動ロードされる。
**長くしすぎないこと**（トップ CLAUDE.md と合わせて毎回コンテキストを食う）。

---

## パッケージ構造（Package-by-Feature）

```
com.example.photomanagement/
├── PhotoManagementApplication.java
├── common/         横断的関心事 (error, web, logging)
├── config/         Spring の Config クラスのみ
├── health/         /api/health
├── photo/          (M3 で追加)
├── album/          (M4)
├── auth/           (M2)
└── user/           (M2)
```

- **新しい機能は新パッケージ**: `com.example.photomanagement.<feature>/` に Controller / Service / Mapper / DTO / 値オブジェクトをまとめる。
- 機能間の依存は明示的に import。
- `common/` は他 feature に依存しない（ArchUnit で強制）。

---

## レイヤ責務（シンプル 3-layer）

| レイヤ | クラス名サフィックス | 役割 |
| --- | --- | --- |
| Controller | `*Controller` | HTTP I/O、DTO ↔ Domain 変換、認可 |
| Service | `*Service` | ビジネスロジック、**トランザクション境界**（`@Transactional`） |
| Mapper | `*Mapper` | MyBatis インターフェース、SQL ↔ POJO |

- **Domain** = Mapper が返す POJO `record`（振る舞いを持たせない）。
- 重要な ID/値は値オブジェクトに包む（例: `PhotoId`, `StorageKey`, `Rating`, `Email`）→ 詳細は `docs/recipes/add-value-object.md`。

---

## 命名

- DTO: `XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse`, `XxxSummary`
- 内部 POJO（entity 相当）: `Xxx`
- 例外: `XxxException`（`DomainException` を継承する）

---

## ArchUnit が強制しているルール

**人間が覚える必要はない**（破ったら `make arch` / CI が落ちる）。実体は `src/test/java/com/example/photomanagement/arch/ArchitectureTest.java`。

要旨:
- Controller → Mapper 直接呼び禁止（Service 経由）
- Mapper → Controller / Service 依存禁止
- `@RestController` / `@Service` / `@Mapper` クラスは命名規約に従う
- feature パッケージ間の循環依存禁止
- `common/` は他 feature に依存しない

詳細は `docs/adr/0004-archunit-enforcement.md`。

---

## エラーレスポンス（RFC 9457 ProblemDetails）

すべて `application/problem+json` で返す。最小例:

```json
{
  "type": "https://photo-management.example/problems/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Photo 123 not found",
  "errorCode": "NOT_FOUND",
  "requestId": "8f3c1c..."
}
```

- 投げ方: `throw new NotFoundException("...")` などの `DomainException` サブクラス。
- 変換: `common/error/ProblemDetailsAdvice` が自動で行う。
- バリデーション失敗時は `errors[]` に field/message が入る。

---

## ロギング

- `logback-spring.xml` でプロファイル分岐。
  - `prod`: JSON 出力
  - それ以外: 人間可読
- すべてのログに `requestId`（MDC 経由）が出る。
- `X-Request-Id` はリクエストヘッダから採用、無ければ生成、レスポンスにエコー。

---

## テスト

- 既存: `@SpringBootTest` + Testcontainers（実 PostgreSQL）。
- スライステストを段階的に: Controller は `@WebMvcTest`、Mapper は `@MybatisTest`。
- 命名: 全部 `*Test`（`*IT` は使わない）。
- アサーション: AssertJ。

---

## 詳細を知りたいとき

- アーキ全体像: `docs/architecture.md`
- 設計の Why: `docs/adr/`
- 「〜を追加する」レシピ:
  - 新規 API: `docs/recipes/add-api.md`
  - 新規 DB マイグレーション: `docs/recipes/add-migration.md`
  - 値オブジェクト導入: `docs/recipes/add-value-object.md`

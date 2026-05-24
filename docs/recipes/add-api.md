# 新規 API を追加する

ここでは「写真にコメントを付ける」`POST /api/photos/{id}/comments` を例にする想定。
パッケージ名 / クラス名は実際の機能に置き換えること。

## 手順

1. **feature パッケージ作成**: 既存になければ `backend/src/main/java/com/example/photomanagement/<feature>/` を新規作成。`package-info.java` に `@NullMarked` を付ける。

2. **DTO / Domain / 値オブジェクト**
   - リクエスト/レスポンス DTO: `XxxCreateRequest`, `XxxResponse` 等。
   - 内部 POJO（Mapper が返す entity 相当）: `Xxx` (`record`)。
   - 値オブジェクトが必要なら同パッケージに（`docs/recipes/add-value-object.md` 参照）。

3. **Mapper** (MyBatis interface): `XxxMapper`
   - `@Mapper` 注釈付き interface。
   - 動的 SQL は XML マッパー（`backend/src/main/resources/com/example/photomanagement/<feature>/XxxMapper.xml`）に書く。
   - 単純な SQL はアノテーション (`@Select` 等) でもよい。

4. **Service**: `XxxService`
   - `@Service` 注釈、コンストラクタインジェクションで `XxxMapper` を受け取る。
   - 書き込みメソッドに `@Transactional`、読み取りは `@Transactional(readOnly = true)`。
   - 異常系は `DomainException` のサブクラスを `throw`。

5. **Controller**: `XxxController`
   - `@RestController @RequestMapping("/api/<feature>")`。
   - Request DTO に `@Valid` を付ける。
   - 認可チェック（M2 以降は `@PreAuthorize` 等）。
   - 戻り値は Response DTO。

6. **テスト**
   - Controller スライス: `@WebMvcTest(XxxController.class)` + MockMvc。
   - Mapper スライス: `@MybatisTest` + Testcontainers。
   - 統合テスト: 必要に応じて `@SpringBootTest` + Testcontainers。
   - すべて `*Test` 命名。

7. **OpenAPI 反映確認**
   - `springdoc-openapi` が自動で `/v3/api-docs` と `/swagger-ui.html` を生成する。
   - フィールドに `@Schema(description=...)` を付けると説明が出る。

8. **`make check` 緑を確認** してコミット。

## ArchUnit との関係

- Controller から Mapper を直接 import すると `make arch` で落ちる → 必ず Service を挟む。
- クラス名のサフィックスを守らないと落ちる（`*Controller` / `*Service` / `*Mapper`）。

## 既存例

`health/HealthController` は最小例（Service / Mapper 無し）。実装感を見るには M3 以降の `photo/` を参照。

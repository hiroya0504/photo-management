# ADR 0004: ArchUnit でアーキ規約を CI 強制

- **Status**: Accepted (2026-05-23)

## Context

Package-by-Feature + シンプル 3-layer の規約（ADR 0002, 0003）を CLAUDE.md / コードレビューだけで守ろうとすると、人間（および Claude Code）の注意力に依存する。CRUD 機能を増やしていくにつれて規約逸脱が静かに蓄積し、リファクタコストが上がる。

## Decision

**ArchUnit のテストで CI 強制する**。`src/test/java/com/example/photomanagement/arch/ArchitectureTest.java` がルールの単一の真実源。

## ルール一覧（実装はテストコードを参照）

1. `..controller..` パッケージ / `*Controller` クラスは `..mapper..` に直接依存しない（Service 経由）。
2. `..mapper..` は `..controller..` / `..service..` に依存しない。
3. `@RestController` クラスは `*Controller` で終わる名前。
4. `@Service` クラスは `*Service` で終わる名前。
5. MyBatis `@Mapper` interface は `*Mapper` で終わる名前。
6. feature パッケージ間（`photo`, `album`, `auth`, `user`, `health`）に循環依存禁止。
7. `common..` は他 feature に依存しない。

## Consequences

### Positive

- 規約違反が PR の CI で即座に落ちる（人間レビュー前に気付ける）。
- CLAUDE.md / ドキュメントから「規約 7 つを覚える」を消せる（テストが真実）。
- ルール追加・変更が `git diff` で明示的に履歴に残る。
- 新規参加者（または Claude Code）が手探りせずに済む（壊したら教えてもらえる）。

### Negative

- 既存の正当な例外パターンが出てきたら、ルール側を緩める判断が要る。
- ArchUnit の DSL に慣れが必要。

### Notes

- ルール追加は躊躇なくこのファイル + `ArchitectureTest.java` を更新する。
- ルールを **緩める** ときは ADR に追記して理由を残す（後で締め直せるように）。
- 設定: `@AnalyzeClasses(packages = "com.example.photomanagement", importOptions = {ImportOption.DoNotIncludeTests.class})` でテストコードは検査対象外。

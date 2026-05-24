# ADR 0001: MyBatis を採用、JPA は採用しない

- **Status**: Accepted (2026-05-23)

## Context

Spring Boot で DB アクセス層を組むにあたって、JPA (Hibernate) を使う「業界標準」と、MyBatis で SQL を明示的に書く方針のどちらを取るか。

このプロジェクトはバックエンドスキル向上が副次目的。検索系（EXIF の範囲指定、複数タグ AND/OR、cursor pagination）が中核機能であり、PostgreSQL 固有の機能（`JSONB`, GIN index, window 関数）を活用する余地が大きい。

## Decision

**MyBatis (mybatis-spring-boot-starter) を採用**。`@Mapper` interface に SQL をアノテーション or XML で記述する。

## Consequences

### Positive

- 発行される SQL がコードで確認できる（隠蔽されない）。Hibernate の N+1 問題やオートフラッシュタイミング等のブラックボックスを避けられる。
- PostgreSQL 機能をフル活用しやすい（JSONB, GIN, partial index, CTE 等）。
- 動的検索（EXIF 範囲、多タグ絞り込み）が MyBatis の `<if>` / `<foreach>` で素直に書ける。
- JPA のエンティティライフサイクル（managed/detached）を意識しなくて良い。

### Negative

- 単純な CRUD でも SQL を書く必要があり、初期コストは JPA より高い。
- スキーマ変更時に Mapper の SQL を手で追従する必要がある（→ 統合テスト + ArchUnit で補う）。
- 「Spring と言えば JPA」の業界知識ベースから外れる（採用面接で説明を求められる）。

### Mitigations

- 統合テスト（Testcontainers + 実 PostgreSQL）でマイグレーション ↔ Mapper の整合性を CI で担保。
- `mybatis-spring-boot-starter-test` の `@MybatisTest` でスライステスト可能。

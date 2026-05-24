# ADR 0002: Package-by-Feature を採用

- **Status**: Accepted (2026-05-23)

## Context

バックエンドのパッケージ構造として、よく見る 2 つの選択肢:

- **Package-by-Layer**: `controller/`, `service/`, `mapper/`, `dto/` を技術レイヤで切る。
- **Package-by-Feature**: `photo/`, `album/`, `user/` を機能で切り、各機能内に `Controller`/`Service`/`Mapper` を置く。

## Decision

**Package-by-Feature を採用**。

```
com.example.photomanagement/
├── common/         横断的関心事
├── config/         Spring Config
├── health/         feature
├── photo/          feature
├── album/          feature
└── ...
```

## Consequences

### Positive

- 機能追加時に触るのが 1 ディレクトリで済む（認知負荷低）。
- 機能間の依存が import 文に現れるため、結合が暴れたらすぐ見える。
- 将来、特定機能をマイクロサービスとして切り出す際の境界が明確。
- 「Photo を直すとき Album のファイルが視界に入らない」ノイズ削減効果。

### Negative

- 「同じレイヤのクラスを一覧したい」が package を跨ぐ。IDE 検索でカバー可能。
- Package-by-Layer に慣れたエンジニアには最初違和感があるかもしれない。

### Enforcement

- 命名規約（`*Controller` / `*Service` / `*Mapper`）は ADR 0004 (ArchUnit) で機械的に検査。
- 機能パッケージ間の循環依存は ArchUnit の `slices()` ルールで禁止。
- `common/` から feature への依存は ArchUnit で禁止（共通ライブラリが特定機能を知ってはいけない）。

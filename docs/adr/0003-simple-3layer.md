# ADR 0003: シンプル 3-layer (Controller/Service/Mapper) を採用、DDD-lite は採用しない

- **Status**: Accepted (2026-05-23)

## Context

機能内のアーキパターンとして:

- **シンプル 3-layer**: Controller → Service → Mapper → DB。Domain はマッパーが返す POJO の record。ビジネスロジックは Service に書く。
- **DDD-lite**: Domain 層を明示し、Aggregate / Value Object / Repository を導入。Service は薄いオーケストレータ、ビジネスルールは Aggregate のメソッドに集約。

このプロジェクトは写真の CRUD + 検索 + 認可が中心で、複雑なビジネスルール（出荷ロジックや課金計算等）は想定しない。学習目的もあるが、DDD は前提知識が多く設定/記述が増える割に CRUD アプリでは恩恵が薄い。

## Decision

**シンプル 3-layer を採用**。ただし以下のハイブリッド:

- **値オブジェクト（VO）は積極導入**。`PhotoId`, `StorageKey`, `Rating`, `Email` 等は record で包む。
- POJO は record として「データの入れ物」に徹し、振る舞いは持たせない。
- ビジネスロジック（認可チェック、ステート遷移）は Service に置く。

## Consequences

### Positive

- Spring の王道で、ドキュメント・サンプル・チームの常識と整合。
- Controller / Service / Mapper の責務が一目瞭然。
- レビュー観点がシンプル（「Service に書いてるか」「Controller に直接 SQL 呼んでないか」）。
- VO だけ導入することで、ID 取り違えや範囲外バリデーション等の事故は防げる。

### Negative

- ビジネスロジックが増えると Service が肥大化しやすい（"anemic domain model" の典型）。
- 振る舞いを Domain に寄せたくなったときの再設計コスト。

### Mitigations

- Service が 300 行を超えたら、責務を切り出す（別 Service、もしくは値オブジェクトのメソッド化）。
- 必要を感じたら ADR を書き直して DDD-lite へ移行（記録として残せばよい）。

## 例

```java
@Service
public class PhotoService {
  Photo assignToAlbum(PhotoId photoId, AlbumId albumId, UserId actor) {
    Photo p = photoMapper.findById(photoId);
    if (!p.ownerUserId().equals(actor)) {
      throw new ForbiddenException("Cannot modify photo of another user");
    }
    photoMapper.attachToAlbum(photoId, albumId);
    return p;
  }
}

public record Photo(
    PhotoId id, UserId ownerUserId, StorageKey storageKey, Rating rating) {}
```

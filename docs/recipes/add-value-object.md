# 値オブジェクトを導入する

「単なる `Long` / `String` だと取り違えやすい」「不変条件を型で表現したい」ときに `record` で包む。

## 最小例: `PhotoId`

```java
package com.example.photomanagement.photo;

public record PhotoId(Long value) {
  public PhotoId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("PhotoId must be positive");
    }
  }
}
```

- 不変条件は **コンパクトコンストラクタ** で。
- フィールドは1個（`value`）が原則。

## バリデーション付きの例: `Email`

```java
package com.example.photomanagement.user;

import java.util.regex.Pattern;

public record Email(String value) {
  private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid email: " + value);
    }
  }
}
```

## 範囲制約付き: `Rating`

```java
package com.example.photomanagement.photo;

public record Rating(int value) {
  public Rating {
    if (value < 1 || value > 5) {
      throw new IllegalArgumentException("Rating must be 1..5");
    }
  }
}
```

## MyBatis との連携

MyBatis は record の `value` フィールドを自動マッピングしない。以下のいずれかで対応:

### A. TypeHandler を書く（推奨）

```java
@MappedTypes(PhotoId.class)
public class PhotoIdTypeHandler extends BaseTypeHandler<PhotoId> {
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, PhotoId v, JdbcType t)
      throws SQLException {
    ps.setLong(i, v.value());
  }

  @Override
  public PhotoId getNullableResult(ResultSet rs, String columnName) throws SQLException {
    long v = rs.getLong(columnName);
    return rs.wasNull() ? null : new PhotoId(v);
  }
  // ... 他の getNullableResult もう2つ ...
}
```

`application.yml` で登録:

```yaml
mybatis:
  type-handlers-package: com.example.photomanagement
```

### B. Mapper 側で素の `Long` を扱い、Service 境界で変換

シンプルだが値オブジェクトのメリットが薄れる。

## 推奨方針

- まず **境界で取り違えやすい ID 型** から導入（`PhotoId`, `AlbumId`, `UserId`）。
- 続いて意味を持つ文字列型（`Email`, `StorageKey`）。
- 数値の範囲制約があるもの（`Rating`）。
- 闇雲に何でも record で包まない（過剰設計になる）。

## 関連

- `docs/architecture.md#値オブジェクト方針`
- ADR は今のところ無し（必要になったら起こす）

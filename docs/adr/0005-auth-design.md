# ADR 0005: M2 認証・認可の設計

- **Status**: Accepted (2026-05-31)

## Context

M2 で認証基盤を実装する。要件は要件定義 7.8 / 7.9 に詳細化済み。本 ADR はその設計判断のうち、コードを読むだけでは「なぜそうなっているか」が分かりにくいものを記録する（手順・網羅的な仕様は 7.8 / 7.9 が真実源）。

実装は 2 つの PR に分割した:

- **PR 1 (Step 1-7)**: スキーマ (V2)、auth サービス一式、`AuthController`、`SecurityConfig`。
- **PR 2 (Step 8-12)**: `UserController`（自プロフィール 4 endpoint）、`AdminUserController`、E2E、本 ADR。

## Decisions

### 1. トークン輸送（Option B）

- **Access**: `Authorization: Bearer <JWT>` ヘッダ。HS256、TTL 15 分。FE はメモリ保持（localStorage 禁止）。
- **Refresh**: HttpOnly Cookie。32 byte ランダム（DB には SHA-256 hex のみ保存）。TTL 7 日 sliding。
- JWT claims は `sub` / `iat` / `exp` / `roles` のみ。秘密情報は入れない（base64 デコードで誰でも読めるため）。
- 署名は HS256 + 環境変数シークレット。マルチサーバー / 鍵ローテーションが必要になったら RS256 に移行。

### 2. 認可モデル（5 階層のうち M2 は ①〜③）

| 階層 | 判定 | M2 |
| --- | --- | --- |
| ① 認証 | Spring Security `authenticated()` | ✅ |
| ② System Role | `hasRole('ADMIN')`（`ADMIN`/`USER` の 2 値） | ✅ |
| ③ 所有者 | acting user id を **常にトークンから**取得（リクエストボディ不可）→ 自分のリソースのみ操作 | ✅ |
| ④ AlbumMember Role | `album_members.role`（OWNER/EDITOR/VIEWER） | M8 |
| ⑤ 公開フラグ | `album.visibility` | M8 |

System Role（②）と AlbumMember Role（④）は別軸。公開単位はアルバムのみ（写真個別の公開は無し）。

ADMIN エンドポイントの認可は **`SecurityConfig` の `/api/admin/**` → `hasRole('ADMIN')` マッチャに一元化**し、`@PreAuthorize` は使わない。認可ルールが 1 箇所に集まり、コントローラは認可を持たない。

### 3. Refresh Token Family（盗難検知）

- ログインで `family_id` を新規生成。ローテーションは同 family を引き継ぐ。
- 使用済み (`used_at` 非 NULL) token の再提示 → 盗難/競合とみなし同 family を全 revoke。
- Grace window 5 秒以内の同一 token 再送はネットワーク再送として許容（family kill しない）。
- ログアウトは現在の token のみ revoke（他デバイスは生存）。

### 4. BCrypt をトランザクション外で実行

BCrypt（strength 12, ~100-300ms）を JDBC トランザクション内で回すと、その間 HikariCP コネクションを占有する。署名/login/changePassword では **BCrypt をトランザクション外**で実行し、DB 書き込みだけを `TransactionTemplate` で短く包む。family revoke は `REQUIRES_NEW` で、rotation 失敗時の例外ロールバックに巻き込まれず独立コミットする。

### 5. Refresh Cookie の Path = `/api/auth`（別 PR の security fix）

当初 `Path=/api/auth/refresh` だったため、ブラウザが `/api/auth/logout` に Cookie を送らず、サーバー側 revoke が no-op になっていた（security-review MEDIUM）。Path を `/api/auth` に広げ、login/refresh/logout すべてに Cookie が届くようにした。`HttpOnly + Secure + SameSite=Strict` は据え置き。スコープが異なるため独立 PR で対応。

> 注: MockMvc は Cookie 送信時に Path を無視するため、この path バグは挙動テストでは捕捉できない。回帰ガードは「発行される Set-Cookie の Path 属性 = `/api/auth`」を直接 assert することで担保する。

### 6. `user → auth` 循環を依存性逆転（DIP）で回避 ★本 PR の主判断

`auth` は既に `user` に依存している（`AuthService` が `User`/`UserMapper`/`RoleMapper` を読む）。一方 Step 8 の「パスワード変更」「アカウント削除」は refresh セッションの失効（= `auth` の機能）を要する。`user → auth` を足すと feature スライスの**循環依存**になり、`ArchitectureTest.noFeatureCycles` で落ちる。

**判断**: 反転すべきは `user → auth` の 1 本だけ。**消費者（`user`）が必要能力を port として所有し、提供者（`auth`）が実装する**（DIP）。

- `user.SessionRevoker` ← `auth.RefreshTokenService` が実装
- `user.PasswordChanger` ← `auth.AuthService` が実装

結果、依存エッジは `auth → user` の一方向のみになり循環が消える。BCrypt（`PasswordHasher`）は `auth` 内に閉じたまま境界を越えない。これにより 4 つの `/api/users/me` エンドポイントを予定通り `UserController` に集約でき、かつ security-review 済みの `AuthService.changePassword` を 1 行も移植しない。

**検討した代替案**:

- *両 feature が各自のパッケージに interface を定義*: 不成立。interface でも「使われる側のパッケージに住む」限り依存の向きは変わらない（ArchUnit はバイトコード参照をパッケージ単位で見る）。port は**消費者側**に置く必要がある。
- *account 系を `auth` パッケージに寄せる*: pw 変更は自然だが、Step 9 の admin 削除/凍結（`AdminUserController` も `user`）が同じ循環を再燃させ、admin 機能を `auth` に置くのは筋が悪い。`SessionRevoker` port なら self/admin 両方を 1 つでカバーできる。
- *`noFeatureCycles` を緩める*: 対症療法でガードレールを弱める。不採用。

`PasswordChanger` はメソッド 1 個・呼び出し側 1 箇所で header interface に見えるが、「user-account モジュールがパスワード変更手段を外部に要求する」という port として正当（ヘキサゴナルの定石）。`SessionRevoker` は self-delete / admin-delete / 将来の全デバイスログアウト等で複数呼び出し側を持つ。

## Consequences

### Positive

- feature 間は `auth → user` の一方向のみ。ArchUnit 緑を維持。
- `/api/users/me/*` 4 endpoint が `UserController` に集約。
- BCrypt の重い処理がコネクションプールを圧迫しない。

### Negative / 留意

- `user` が `auth` の実装 bean に実行時依存する（DI で注入）。port の契約が両者の結合点になる。
- **admin のユーザー一覧は offset pagination**。アーキ方針（検索は cursor/keyset）と不整合だが、admin ツール用途として暫定許容。core の一覧 API を作る際に cursor へ揃える（follow-up）。
- アクセストークンは失効不可（最大 15 分有効）。パスワード変更/削除後も残存アクセストークンは期限まで有効 = stateless JWT の文書化済みトレードオフ。

### Notes

- 命名: 本リポジトリは統合テストも `*Test`（`*IT` は使わない）。E2E は `AuthFullFlowTest`。
- M2 で**含まない**もの: メール検証 / パスワードリセット / レート制限 / アカウントロック / 監査ログ / 全デバイスログアウト / MFA / OAuth（要件 7.8.1 参照）。

package com.example.photomanagement.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper {

  @Select(
      """
      SELECT id, user_id, family_id, token_hash, expires_at, used_at, revoked_at, created_at
      FROM refresh_tokens
      WHERE token_hash = #{tokenHash}
      """)
  Optional<RefreshTokenRecord> findByTokenHash(@Param("tokenHash") String tokenHash);

  @Insert(
      """
      INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at)
      VALUES (#{userId}, #{familyId}::uuid, #{tokenHash}, #{expiresAt})
      """)
  void insert(
      @Param("userId") Long userId,
      @Param("familyId") UUID familyId,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") Instant expiresAt);

  @Update(
      """
      UPDATE refresh_tokens
      SET used_at = #{usedAt}
      WHERE id = #{id} AND used_at IS NULL
      """)
  int markUsed(@Param("id") Long id, @Param("usedAt") Instant usedAt);

  /** Revokes every active token in the given family. Used for theft / replay detection. */
  @Update(
      """
      UPDATE refresh_tokens
      SET revoked_at = #{revokedAt}
      WHERE family_id = #{familyId}::uuid AND revoked_at IS NULL
      """)
  int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

  /** Used for logout: revoke just the supplied token, not the rest of the family. */
  @Update(
      """
      UPDATE refresh_tokens
      SET revoked_at = #{revokedAt}
      WHERE id = #{id} AND revoked_at IS NULL
      """)
  int revokeById(@Param("id") Long id, @Param("revokedAt") Instant revokedAt);
}

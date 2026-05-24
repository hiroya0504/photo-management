package com.example.photomanagement.user;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

  @Select(
      """
      SELECT id, email, password_hash, created_at, updated_at, deleted_at
      FROM users
      WHERE id = #{id} AND deleted_at IS NULL
      """)
  Optional<User> findActiveById(@Param("id") Long id);

  @Select(
      """
      SELECT id, email, password_hash, created_at, updated_at, deleted_at
      FROM users
      WHERE email = #{email} AND deleted_at IS NULL
      """)
  Optional<User> findActiveByEmail(@Param("email") String email);

  /**
   * Inserts a new user. The generated id is fetched via a subsequent {@link #findActiveByEmail}
   * call to keep {@link User} immutable.
   */
  @Insert(
      """
      INSERT INTO users (email, password_hash)
      VALUES (#{email}, #{passwordHash})
      """)
  void insert(@Param("email") String email, @Param("passwordHash") String passwordHash);

  @Update(
      """
      UPDATE users
      SET email = #{email}, updated_at = now()
      WHERE id = #{id} AND deleted_at IS NULL
      """)
  int updateEmail(@Param("id") Long id, @Param("email") String email);

  @Update(
      """
      UPDATE users
      SET password_hash = #{passwordHash}, updated_at = now()
      WHERE id = #{id} AND deleted_at IS NULL
      """)
  int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

  @Update(
      """
      UPDATE users
      SET deleted_at = now()
      WHERE id = #{id} AND deleted_at IS NULL
      """)
  int softDelete(@Param("id") Long id);

  @Select(
      """
      SELECT id, email, password_hash, created_at, updated_at, deleted_at
      FROM users
      WHERE deleted_at IS NULL
      ORDER BY id
      LIMIT #{limit} OFFSET #{offset}
      """)
  List<User> listActive(@Param("limit") int limit, @Param("offset") int offset);
}

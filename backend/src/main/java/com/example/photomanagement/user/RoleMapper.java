package com.example.photomanagement.user;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {

  @Select("SELECT id FROM roles WHERE name = #{name}")
  Optional<Short> findIdByName(@Param("name") String name);

  @Insert(
      """
      INSERT INTO user_roles (user_id, role_id)
      VALUES (#{userId}, #{roleId})
      ON CONFLICT (user_id, role_id) DO NOTHING
      """)
  void assignRole(@Param("userId") Long userId, @Param("roleId") Short roleId);

  @Select(
      """
      SELECT r.name
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      WHERE ur.user_id = #{userId}
      ORDER BY r.name
      """)
  List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}

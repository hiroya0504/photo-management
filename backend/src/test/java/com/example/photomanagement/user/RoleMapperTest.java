package com.example.photomanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.photomanagement.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RoleMapperTest {

  @Autowired private UserMapper userMapper;
  @Autowired private RoleMapper roleMapper;

  @Test
  void seedRolesArePresent() {
    assertThat(roleMapper.findIdByName("ADMIN")).isPresent();
    assertThat(roleMapper.findIdByName("USER")).isPresent();
    assertThat(roleMapper.findIdByName("UNKNOWN")).isEmpty();
  }

  @Test
  void assignAndListRoles() {
    userMapper.insert("grace@example.com", "hash");
    Long userId = userMapper.findActiveByEmail("grace@example.com").orElseThrow().id();
    Short userRoleId = roleMapper.findIdByName("USER").orElseThrow();
    Short adminRoleId = roleMapper.findIdByName("ADMIN").orElseThrow();

    roleMapper.assignRole(userId, userRoleId);
    roleMapper.assignRole(userId, adminRoleId);

    List<String> roles = roleMapper.findRoleNamesByUserId(userId);

    assertThat(roles).containsExactly("ADMIN", "USER");
  }

  @Test
  void assignRoleIsIdempotent() {
    userMapper.insert("heidi@example.com", "hash");
    Long userId = userMapper.findActiveByEmail("heidi@example.com").orElseThrow().id();
    Short userRoleId = roleMapper.findIdByName("USER").orElseThrow();

    roleMapper.assignRole(userId, userRoleId);
    roleMapper.assignRole(userId, userRoleId); // duplicate insert; should be a no-op

    assertThat(roleMapper.findRoleNamesByUserId(userId)).containsExactly("USER");
  }
}

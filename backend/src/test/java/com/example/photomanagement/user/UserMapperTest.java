package com.example.photomanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.photomanagement.TestcontainersConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserMapperTest {

  @Autowired private UserMapper userMapper;

  @Test
  void insertAndFindByEmail() {
    userMapper.insert("alice@example.com", "hashed-pw-1");

    Optional<User> found = userMapper.findActiveByEmail("alice@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().email()).isEqualTo("alice@example.com");
    assertThat(found.get().passwordHash()).isEqualTo("hashed-pw-1");
    assertThat(found.get().deletedAt()).isNull();
    assertThat(found.get().createdAt()).isNotNull();
  }

  @Test
  void findByIdReturnsActiveUser() {
    userMapper.insert("bob@example.com", "hashed-pw-2");
    User inserted = userMapper.findActiveByEmail("bob@example.com").orElseThrow();

    Optional<User> byId = userMapper.findActiveById(inserted.id());

    assertThat(byId).isPresent();
    assertThat(byId.get().id()).isEqualTo(inserted.id());
  }

  @Test
  void softDeleteHidesUserFromActiveQueries() {
    userMapper.insert("carol@example.com", "hashed-pw-3");
    User carol = userMapper.findActiveByEmail("carol@example.com").orElseThrow();

    int updated = userMapper.softDelete(carol.id());

    assertThat(updated).isEqualTo(1);
    assertThat(userMapper.findActiveByEmail("carol@example.com")).isEmpty();
    assertThat(userMapper.findActiveById(carol.id())).isEmpty();
  }

  @Test
  void emailCanBeReusedAfterSoftDelete() {
    userMapper.insert("dave@example.com", "hashed-pw-4a");
    User original = userMapper.findActiveByEmail("dave@example.com").orElseThrow();
    userMapper.softDelete(original.id());

    userMapper.insert("dave@example.com", "hashed-pw-4b");
    Optional<User> reused = userMapper.findActiveByEmail("dave@example.com");

    assertThat(reused).isPresent();
    assertThat(reused.get().id()).isNotEqualTo(original.id());
    assertThat(reused.get().passwordHash()).isEqualTo("hashed-pw-4b");
  }

  @Test
  void updateEmailChangesActiveLookup() {
    userMapper.insert("erin@example.com", "hashed-pw-5");
    User erin = userMapper.findActiveByEmail("erin@example.com").orElseThrow();

    int updated = userMapper.updateEmail(erin.id(), "erin+new@example.com");

    assertThat(updated).isEqualTo(1);
    assertThat(userMapper.findActiveByEmail("erin@example.com")).isEmpty();
    assertThat(userMapper.findActiveByEmail("erin+new@example.com")).isPresent();
  }

  @Test
  void updatePasswordHashPersists() {
    userMapper.insert("frank@example.com", "old-hash");
    User frank = userMapper.findActiveByEmail("frank@example.com").orElseThrow();

    userMapper.updatePasswordHash(frank.id(), "new-hash");

    assertThat(userMapper.findActiveById(frank.id()).orElseThrow().passwordHash())
        .isEqualTo("new-hash");
  }
}

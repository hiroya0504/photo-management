package com.example.photomanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PhotoManagementApplicationTests {

  @Test
  void contextLoads() {
    // Spring context boots successfully against a real PostgreSQL provisioned via Testcontainers.
    // Flyway migrations are applied during context startup; failure here surfaces migration errors.
  }
}

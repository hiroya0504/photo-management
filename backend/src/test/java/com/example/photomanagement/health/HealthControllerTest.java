package com.example.photomanagement.health;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photomanagement.TestcontainersConfiguration;
import com.example.photomanagement.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthReturnsOk() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void requestIdIsGeneratedWhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    RequestIdFilter.HEADER,
                    matchesPattern("^[0-9a-fA-F-]{36}$"))); // UUID v4 length
  }

  @Test
  void requestIdIsEchoedWhenSupplied() throws Exception {
    String supplied = "test-request-id-abc";
    mockMvc
        .perform(get("/api/health").header(RequestIdFilter.HEADER, supplied))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestIdFilter.HEADER, supplied));
  }
}

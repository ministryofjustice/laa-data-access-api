package uk.gov.justice.laa.dstew.access.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import uk.gov.justice.laa.dstew.access.AccessApp;

@SpringBootTest(classes = AccessApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ApplicationControllerIntegrationTest {

  private static String existingApplicationUri;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  // Pre-insert a valid status code
  @BeforeAll
  void setupStatusCode() {
    jdbcTemplate.update(
        "INSERT INTO status_code_lookup (id, code, description) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
        UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
        "SUBMITTED",
        "Submitted"
    );
  }

  private String buildApplicationJson() {
    return "{"
        + "\"id\": \"" + UUID.randomUUID() + "\","
        + "\"statusId\": \"123e4567-e89b-12d3-a456-426614174000\","
        + "\"applicationContent\": {"
        + "\"first_name\": \"John\","
        + "\"last_name\": \"Doe\","
        + "\"application_id\": \"" + UUID.randomUUID() + "\""
        + "}"
        + "}";
  }

  @Test
  @Order(1)
  @WithAnonymousUser
  void whenNoAuth_shouldReturn401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications"))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
  }

  @Test
  @Order(2)
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetAllApplications() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications"))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  @Test
  @Order(3)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldCreateApplication() throws Exception {
    existingApplicationUri = mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v0/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildApplicationJson()))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertFalse(existingApplicationUri.isEmpty());
  }

  @Test
  @Order(4)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldCreateItem() throws Exception {
    String payload = buildApplicationJson();
    String location = mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v0/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertFalse(location.isEmpty());
  }

  @Test
  @Order(5)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldUpdateApplication() throws Exception {
    if (existingApplicationUri != null) {
      String updatePayload = "{"
          + "\"statusId\": \"123e4567-e89b-12d3-a456-426614174000\","
          + "\"applicationContent\": {"
          + "\"first_name\": \"John\","
          + "\"last_name\": \"Doe\","
          + "\"application_id\": \"" + UUID.randomUUID() + "\""
          + "},"
          + "\"schemaVersion\": 1"
          + "}";
      mockMvc.perform(MockMvcRequestBuilders.patch(existingApplicationUri)
              .contentType(MediaType.APPLICATION_JSON)
              .content(updatePayload))
          .andExpect(MockMvcResultMatchers.status().isNoContent());
    }
  }

  @Test
  @Order(6)
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetItem() throws Exception {
    if (existingApplicationUri != null) {
      mockMvc.perform(MockMvcRequestBuilders.get(existingApplicationUri))
          .andExpect(MockMvcResultMatchers.status().isOk())
          .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.first_name").value("John"))
          .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.last_name").value("Doe"))
          .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNotEmpty());
    }
  }

  @Test
  @Order(7)
  @WithMockUser
  void when_incorrect_authorities_getAllItems_should_return_403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
  }
}

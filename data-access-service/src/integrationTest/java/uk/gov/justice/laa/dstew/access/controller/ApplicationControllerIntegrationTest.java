package uk.gov.justice.laa.dstew.access.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.access.AccessApp;

@SpringBootTest(classes = AccessApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApplicationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  private static String existingApplicationUri;

  private final String examplePayload = """
        {
            "provider_firm_id": "firm-002",
            "provider_office_id": "office-201",
            "client_id": "345e6789-eabb-34d5-a678-426614174333",
            "application_content": {"example_field":"example_value"}
        }
    """;

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Order(1)
  void shouldCreateItem() throws Exception {
    existingApplicationUri = mockMvc
        .perform(post("/api/v0/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(examplePayload)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertFalse(existingApplicationUri.isEmpty());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Order(2)
  void shouldGetAllItems() throws Exception {
    mockMvc
        .perform(get("/api/v0/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(1)));
  }

  @Test
  @WithAnonymousUser
  @Order(2)
  void whenNoAuthGetAllItems_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v0/applications"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  @Order(2)
  void whenIncorrectAuthoritiesGetAllItems_shouldReturn403() throws Exception {
    mockMvc.perform(get("/api/v0/applications"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Order(2)
  void shouldGetItem() throws Exception {
    mockMvc.perform(get(existingApplicationUri))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.provider_firm_id").value("firm-002"))
        .andExpect(jsonPath("$.client_id").value("345e6789-eabb-34d5-a678-426614174333"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Order(2)
  void shouldUpdateItem() throws Exception {
    mockMvc.perform(patch(existingApplicationUri)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status_code\": \"IN_PROGRESS\"}")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldDeleteItem() throws Exception {
    mockMvc.perform(delete("/api/v0/applications/345e6789-eabb-34d5-a678-426614174333"))
        .andExpect(status().isNoContent());
  }
}

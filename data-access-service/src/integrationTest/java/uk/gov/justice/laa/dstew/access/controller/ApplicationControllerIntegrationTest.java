package uk.gov.justice.laa.dstew.access.controller;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.access.AccessApp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AccessApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApplicationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private static String existingApplicationUri;

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Order(1)
  void shouldGetAllApplications() throws Exception {
    mockMvc.perform(get("/api/v0/applications"))
        .andExpect(status().isOk());
  }

  @Test
  @WithAnonymousUser
  void whenNoAuth_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/v0/applications"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Order(2)
  void shouldCreateApplication() throws Exception {
    existingApplicationUri = mockMvc.perform(post("/api/v0/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusId\": \"123e4567-e89b-12d3-a456-426614174000\"}"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldUpdateApplication() throws Exception {
    if (existingApplicationUri != null) {
      mockMvc.perform(patch(existingApplicationUri)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"statusId\": \"123e4567-e89b-12d3-a456-426614174000\"}"))
          .andExpect(status().isNoContent());
    }
  }
}

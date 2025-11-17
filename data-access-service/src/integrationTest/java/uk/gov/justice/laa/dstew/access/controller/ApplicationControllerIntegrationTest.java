package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApplicationControllerIntegrationTest {

  private static String existingApplicationUri;

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ApplicationSummaryRepository repository;

  private String buildApplicationJson() {
    return "{"
        + "\"id\": \"" + UUID.randomUUID() + "\","
        + "\"status\": \"SUBMITTED\","
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

  ApplicationSummaryEntity createApplicationSummaryEntity(ApplicationStatus status) {
    ApplicationSummaryEntity entity;

    entity = new ApplicationSummaryEntity();
    entity.setId(UUID.randomUUID());
    entity.setApplicationReference(UUID.randomUUID().toString());
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());
    entity.setStatus(status);

    return entity;
  }

  List<ApplicationSummaryEntity> createMixedStatusSummaryList() {
    List<ApplicationSummaryEntity> entities = new ArrayList<>();

    entities.add(createApplicationSummaryEntity(ApplicationStatus.IN_PROGRESS));
    entities.add(createApplicationSummaryEntity(ApplicationStatus.IN_PROGRESS));
    entities.add(createApplicationSummaryEntity(ApplicationStatus.SUBMITTED));
    entities.add(createApplicationSummaryEntity(ApplicationStatus.IN_PROGRESS));
    return entities;
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetAllApplicationsWhenNoFilter() throws Exception {

    when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(createMixedStatusSummaryList()));
    when(repository.count(any(Specification.class))).thenReturn(4L);

    mockMvc
      .perform(MockMvcRequestBuilders.get("/api/v0/applications"))
      .andExpect(MockMvcResultMatchers.status().isOk())
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.*", hasSize(2)))
      .andExpect(jsonPath("$.applications", hasSize(4)));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetAllApplicationsWhenFilterIsSubmitted() throws Exception {

    when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(createMixedStatusSummaryList()));
    when(repository.count(any(Specification.class))).thenReturn(4L);

    mockMvc
            .perform(MockMvcRequestBuilders.get("/api/v0/applications?status=SUBMITTED"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.applications", hasSize(4)));
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
  void getItem_should_return_401_when_missing_credentials() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications/019a2b5e-d126-71c7-89c2-500363c172f1"))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
  }

  @Test
  @WithMockUser()
  void getItem_should_return_403_when_user_does_not_have_required_roles() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications/019a2b5e-d126-71c7-89c2-500363c172f1"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
  }

  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  void getItem_should_return_404_when_application_does_not_exist() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications/019a2b5e-d126-71c7-89c2-500363c172f1"))
        .andExpect(MockMvcResultMatchers.status().isNotFound())
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Not found"))
        .andExpect(jsonPath("$.detail")
            .value("No application found with id: 019a2b5e-d126-71c7-89c2-500363c172f1"));
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
          + "\"status\": \"SUBMITTED\","
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
          .andExpect(jsonPath("$.applicationContent.first_name").value("John"))
          .andExpect(jsonPath("$.applicationContent.last_name").value("Doe"))
          .andExpect(jsonPath("$.id").isNotEmpty());
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

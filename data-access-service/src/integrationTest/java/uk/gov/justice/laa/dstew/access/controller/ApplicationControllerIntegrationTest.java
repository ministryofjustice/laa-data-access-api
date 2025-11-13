package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=false")
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

  @Mock
  private ApplicationSummaryRepository applicationSummaryRepository;

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

  @Test
  @Order(2)
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetAllApplications() throws Exception {
    ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
    firstEntity.setId(UUID.randomUUID());
    firstEntity.setApplicationReference("appRef1");
    firstEntity.setCreatedAt(Instant.now());
    firstEntity.setModifiedAt(Instant.now());
    firstEntity.setStatus(ApplicationStatus.SUBMITTED);
    
    ApplicationSummary firstSummary = new ApplicationSummary();
    firstSummary.setApplicationId(firstEntity.getId());
    firstSummary.setApplicationReference(firstEntity.getApplicationReference());
    firstSummary.setCreatedAt(firstEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
    firstSummary.setModifiedAt(firstEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
    firstSummary.setApplicationStatus(ApplicationStatus.IN_PROGRESS);
    
    ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
    secondEntity.setId(UUID.randomUUID());
    secondEntity.setApplicationReference("appRef2");
    secondEntity.setCreatedAt(Instant.now());
    secondEntity.setModifiedAt(Instant.now());
    secondEntity.setStatus(ApplicationStatus.SUBMITTED);

    ApplicationSummary secondSummary = new ApplicationSummary();
    secondSummary.setApplicationId(secondEntity.getId());
    secondSummary.setApplicationReference(secondEntity.getApplicationReference());
    secondSummary.setCreatedAt(secondEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
    secondSummary.setModifiedAt(secondEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
    secondSummary.setApplicationStatus(ApplicationStatus.IN_PROGRESS);
    
    Pageable pageDetails = PageRequest.of(1, 1);
    
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(List.of(firstEntity, secondEntity));

    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);
    when(repository.count(any(Specification.class))).thenReturn(2L);
    
    when(mapper.toApplicationSummary(firstEntity)).thenReturn(firstSummary);
    when(mapper.toApplicationSummary(secondEntity)).thenReturn(secondSummary);

    List<ApplicationSummary> result = classUnderTest.getAllApplications(ApplicationStatus.IN_PROGRESS, 1, 1);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getApplicationId()).isEqualTo(firstEntity.getId());
    assertThat(result.get(1).getApplicationId()).isEqualTo(secondEntity.getId());
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
        .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Not found"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
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

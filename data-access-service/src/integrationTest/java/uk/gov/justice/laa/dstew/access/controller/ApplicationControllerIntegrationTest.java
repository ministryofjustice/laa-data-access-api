package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedIndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
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

  @Autowired
  private ApplicationRepository applicationRepository;


  private String buildApplicationJson() {
    return buildApplicationJson(false);
  }

  private String buildApplicationJson(boolean withIndividuals) {
    StringBuilder linkedIndividualsJson = new StringBuilder();

    if (withIndividuals) {
      linkedIndividualsJson.append(", \"linked_individuals\": [")
          .append("{")
          .append("\"firstName\": \"John\",")
          .append("\"lastName\": \"Doe\",")
          .append("\"dateOfBirth\": \"1990-01-01\",")
          .append("\"details\": {\"email\": \"john.doe@example.com\"}")
          .append("},")
          .append("{")
          .append("\"firstName\": \"Jane\",")
          .append("\"lastName\": \"Doe\",")
          .append("\"dateOfBirth\": \"1992-02-02\",")
          .append("\"details\": {\"email\": \"jane.doe@example.com\"}")
          .append("}")
          .append("]");
    }
    return "{"
        + "\"id\": \"" + UUID.randomUUID() + "\","
        + "\"status\": \"SUBMITTED\","
        + "\"applicationReference\": \"app_ref\","
        + "\"applicationContent\": {"
        + "\"first_name\": \"John\","
        + "\"last_name\": \"Doe\","
        + "\"application_id\": \"" + UUID.randomUUID() + "\""
        + "}"
        + linkedIndividualsJson
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

  List<ApplicationSummaryEntity> createSubmittedStatusSummaryList() {
    List<ApplicationSummaryEntity> entities = new ArrayList<>();

    entities.add(createApplicationSummaryEntity(ApplicationStatus.SUBMITTED));
    entities.add(createApplicationSummaryEntity(ApplicationStatus.SUBMITTED));
    entities.add(createApplicationSummaryEntity(ApplicationStatus.SUBMITTED));
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
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.jsonPath("$.*", hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applications", hasSize(4)));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  void shouldGetAllApplicationsWhenFilterIsSubmitted() throws Exception {

    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(createSubmittedStatusSummaryList()));
    when(repository.count(any(Specification.class))).thenReturn(3L);

    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v0/applications?status=SUBMITTED"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.jsonPath("$.*", hasSize(2)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applications", hasSize(3)));
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

    assertThat(existingApplicationUri).isNotEmpty();
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

    assertThat(location.isEmpty());
  }

  @Test
  @Order(5)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldUpdateApplication() throws Exception {
    if (existingApplicationUri != null) {
      String updatePayload = "{"
          + "\"status\": \"SUBMITTED\","
          + "\"applicationContent\": {"
          + "\"applicationReference\": \"app_ref\","
          + "\"first_name\": \"John\","
          + "\"last_name\": \"Doe\","
          + "\"application_id\": \"" + UUID.randomUUID() + "\""
          + "},"
          + "\"schema_version\": \"" + 1 + "\""
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

  @Test
  @Order(8)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter", "APPROLE_ApplicationReader"})
  void shouldUpdateApplication_withContentAndStatus() throws Exception {
    String createPayload = buildApplicationJson();
    String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{"
        + "\"status\": \"IN_PROGRESS\","
        + "\"applicationContent\": {\"first_name\": \"Jane\", \"last_name\": \"Smith\"}"
        + "}";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatePayload))
        .andExpect(MockMvcResultMatchers.status().isNoContent());

    mockMvc.perform(MockMvcRequestBuilders.get(location))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.first_name").value("Jane"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.last_name").value("Smith"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationStatus").value("IN_PROGRESS"));
  }


  @Test
  @Order(9)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter", "APPROLE_ApplicationReader"})
  void shouldUpdateApplication_withContentOnly_statusUnchanged() throws Exception {
    String createPayload = buildApplicationJson();
    String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": {\"first_name\": \"Alice\", \"last_name\": \"Wonder\"} }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatePayload))
        .andExpect(MockMvcResultMatchers.status().isNoContent());

    mockMvc.perform(MockMvcRequestBuilders.get(location))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.first_name").value("Alice"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationContent.last_name").value("Wonder"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.applicationStatus").value("SUBMITTED")); // original status
  }

  @Test
  @Order(10)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldFailUpdate_whenApplicationContentIsNull() throws Exception {
    String createPayload = buildApplicationJson();
    String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": null }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatePayload))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  @Order(11)
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  void shouldFailUpdate_whenApplicationContentIsEmpty() throws Exception {
    String createPayload = buildApplicationJson();
    String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": {} }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatePayload))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Transactional
  @Order(12)
  void shouldReturnLinkedIndividuals() throws Exception {

    UUID appId = UUID.randomUUID();

    ApplicationEntity app = new ApplicationEntity();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setApplicationContent(Map.of("foo", "bar"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    IndividualEntity ind1 = new IndividualEntity();
    ind1.setId(UUID.randomUUID());
    ind1.setFirstName("John");
    ind1.setLastName("Doe");
    ind1.setDateOfBirth(LocalDate.of(1990, 1, 1));
    ind1.setIndividualContent(Map.of("email", "john.doe@example.com"));

    IndividualEntity ind2 = new IndividualEntity();
    ind2.setId(UUID.randomUUID());
    ind2.setFirstName("Jane");
    ind2.setLastName("Doe");
    ind2.setDateOfBirth(LocalDate.of(1992, 2, 2));
    ind2.setIndividualContent(Map.of("email", "jane.doe@example.com"));

    app.setIndividuals(Set.of(ind1, ind2));

    applicationRepository.saveAndFlush(app);

    mockMvc.perform(get("/api/v0/applications/" + appId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.individuals.length()").value(2))
        .andExpect(jsonPath("$.individuals[0].firstName").exists())
        .andExpect(jsonPath("$.individuals[1].firstName").exists());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  @Order(13)
  void shouldReturnEmptyLinkedIndividualsList() throws Exception {

    ApplicationEntity app = new ApplicationEntity();
    UUID appId = UUID.randomUUID();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setSchemaVersion(1);
    app.setApplicationContent(Map.of("first_name", "Alice", "last_name", "Wonder"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    app.setIndividuals(Set.of());
    applicationRepository.save(app);

    mockMvc.perform(get("/api/v0/applications/" + appId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.individuals").isArray())
        .andExpect(jsonPath("$.individuals").isEmpty());
  }


}

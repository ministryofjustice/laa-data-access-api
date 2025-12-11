package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;

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

  @Autowired
  private CaseworkerRepository caseworkerRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private ObjectMapper objectMapper;

  private ApplicationCreateRequest buildApplication() {
    return ApplicationCreateRequest.builder()
          .status(ApplicationStatus.SUBMITTED)
          .applicationReference("app_ref")
          .applicationContent(Map.of(
                  "id", "71489fb1-742e-4e72-8b0a-db7b9a0cd100",
                  "name", "Martin Ronan",
                  "email", "martin.ronan@example.com",
                  "firm_id", "5580f217-5e07-4ff7-8307-9966a1b73f35",
                  "created_at", "2025-09-10T14:33:54.905+01:00",
                  "updated_at", "2025-11-24T09:08:45.308+00:00",
                  "office_codes", "0X395U:2N078D:A123456"
          ))
          .individuals(List.of(
                  Individual.builder()
                          .firstName("John")
                          .lastName("Doe")
                          .dateOfBirth(LocalDate.of(1990, 1, 1))
                          .details(Map.of("contactNumber", "+447123456789"))
                          .build(),
                  Individual.builder()
                          .firstName("Jan")
                          .lastName("Eod")
                          .dateOfBirth(LocalDate.of(1992, 2, 2))
                          .details(Map.of("contactNumber", "+447987654321"))
                          .build()
          ))
          .build();
  }
  private String buildApplicationJson() throws Exception {
    return  objectMapper.writeValueAsString(buildApplication());
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
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": {\"first_name\": \"Alice\", \"last_name\": \"Wonder\"} }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
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
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": null }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
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
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn()
        .getResponse()
        .getHeader("Location");

    assertThat(location).isNotNull();

    String updatePayload = "{ \"applicationContent\": {} }";

    mockMvc.perform(MockMvcRequestBuilders.patch(location)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatePayload))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Transactional
  @Order(12)
  void shouldReturnIndividuals() throws Exception {
    ApplicationEntity app = new ApplicationEntity();
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setApplicationContent(Map.of("foo", "bar"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    IndividualEntity ind1 = new IndividualEntity();
    ind1.setFirstName("John");
    ind1.setLastName("Doe");
    ind1.setDateOfBirth(LocalDate.of(1990, 1, 1));
    ind1.setIndividualContent(Map.of("email", "john.doe@example.com"));

    IndividualEntity ind2 = new IndividualEntity();
    ind2.setFirstName("Jane");
    ind2.setLastName("Doe");
    ind2.setDateOfBirth(LocalDate.of(1992, 2, 2));
    ind2.setIndividualContent(Map.of("email", "jane.doe@example.com"));

    entityManager.persist(ind1);
    entityManager.persist(ind2);

    app.setIndividuals(Set.of(ind1, ind2));
    var appId = applicationRepository.saveAndFlush(app).getId();

    mockMvc.perform(get("/api/v0/applications/" + appId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.individuals.length()").value(2))
        .andExpect(jsonPath("$.individuals[0].firstName").exists())
        .andExpect(jsonPath("$.individuals[1].firstName").exists());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader"})
  @Transactional
  void shouldReturnCaseworkerId() throws Exception {
    CaseworkerEntity caseworkerEntity = CaseworkerEntity.builder().username("caseworker1").build();
    final UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworkerEntity).getId();
    ApplicationEntity app = ApplicationEntity.builder()
                                             .status(ApplicationStatus.SUBMITTED)
                                             .caseworker(caseworkerEntity)
                                             .applicationContent(Map.of("foo", "bar"))
                                             .build();

    final UUID appId = applicationRepository.saveAndFlush(app).getId();

    mockMvc.perform(get("/api/v0/applications/" + appId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseworkerId").value(caseworkerId.toString()));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  @Order(13)
  void shouldReturnEmptyIndividualsList() throws Exception {

    ApplicationEntity app = new ApplicationEntity();
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setSchemaVersion(1);
    app.setApplicationContent(Map.of("first_name", "Alice", "last_name", "Wonder"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    app.setIndividuals(Set.of());
    var appId = applicationRepository.save(app).getId();

    mockMvc.perform(get("/api/v0/applications/" + appId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.individuals").isArray())
        .andExpect(jsonPath("$.individuals").isEmpty());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldAssignCaseworker() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("caseworker_user")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    UUID appId = applicationRepository.saveAndFlush(app).getId();
    
    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(caseworkerId)
                                                             .applicationIds(List.of(appId))
                                                              .eventHistory(
                                                                  EventHistory.builder()
                                                                          .eventDescription("description")
                                                                  .build()
                                                              )
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    ApplicationEntity updated = applicationRepository.findById(appId).orElseThrow();
    assertThat(updated.getCaseworker()).isNotNull();
    assertThat(updated.getCaseworker().getId()).isEqualTo(caseworkerId);
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldAssignMultipleApplicationsToCaseworker() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("caseworker_user")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();
    ApplicationEntity app2 = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    UUID appId = applicationRepository.saveAndFlush(app).getId();
    UUID appId2 = applicationRepository.saveAndFlush(app2).getId();
    
    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(caseworkerId)
                                                             .applicationIds(List.of(appId, appId2))
                                                              .eventHistory(
                                                                      EventHistory.builder()
                                                                      .eventDescription("description")
                                                                      .build()
                                                              )
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    ApplicationEntity updated = applicationRepository.findById(appId).orElseThrow();
    ApplicationEntity updated2 = applicationRepository.findById(appId2).orElseThrow();
    assertThat(updated.getCaseworker()).isNotNull();
    assertThat(updated.getCaseworker().getId()).isEqualTo(caseworkerId);
    assertThat(updated2.getCaseworker()).isNotNull();
    assertThat(updated2.getCaseworker().getId()).isEqualTo(caseworkerId);
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldThrowWhenMultipleAssigningApplicationUnknown() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("caseworker_user")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();
    UUID appId = applicationRepository.saveAndFlush(app).getId();
    UUID appId2 = UUID.randomUUID();
    UUID appId3 = UUID.randomUUID();
    
    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(caseworkerId)
                                                             .applicationIds(List.of(appId, appId2, appId3))
                                                             .eventHistory(EventHistory.builder()
                                                                     .eventDescription("description")
                                                                     .build()
                                                             )
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No application found with ids: " + appId2 + "," + appId3));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReAssignCaseworker() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("caseworker_user")
        .build();
    CaseworkerEntity caseworkerOther = CaseworkerEntity.builder()
        .username("caseworker_user_other")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();
    UUID caseworkerOtherId = caseworkerRepository.saveAndFlush(caseworkerOther).getId();

    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .caseworker(caseworker)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    assertThat(app.getCaseworker().getId()).isEqualTo(caseworkerId);

    UUID appId = applicationRepository.saveAndFlush(app).getId();

    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(caseworkerOtherId)
                                                             .applicationIds(List.of(appId))
                                                             .eventHistory(
                                                               EventHistory.builder()
                                                                .eventDescription("description")
                                                                .build())
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    ApplicationEntity updated = applicationRepository.findById(appId).orElseThrow();
    assertThat(updated.getCaseworker()).isNotNull();
    assertThat(updated.getCaseworker().getId()).isEqualTo(caseworkerOtherId);
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReturn404WhenAssigningToNonExistentApplication() throws Exception {
    UUID missingAppId = UUID.randomUUID();

    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("cw")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(caseworkerId)
                                                             .applicationIds(List.of(missingAppId))
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Not found"))
        .andExpect(jsonPath("$.detail").value("No application found with ids: " + missingAppId));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReturn404WhenCaseworkerDoesNotExist() throws Exception {
    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();
    UUID appId = applicationRepository.saveAndFlush(app).getId();

    UUID missingCwId = UUID.randomUUID();
        CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .caseworkerId(missingCwId)
                                                             .applicationIds(List.of(appId))
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Not found"))
        .andExpect(jsonPath("$.detail").value("No caseworker found with id: " + missingCwId));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReturn400WhenCaseworkerIdMissing() throws Exception {
    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    UUID appId = applicationRepository.saveAndFlush(app).getId();

    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
                                                             .applicationIds(List.of(UUID.randomUUID()))
                                                             .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldUnassignCaseworker() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
        .username("caseworker_user")
        .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
        .status(ApplicationStatus.SUBMITTED)
        .caseworker(caseworker)
        .applicationContent(Map.of("foo", "bar"))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    UUID appId = applicationRepository.saveAndFlush(app).getId();

    assertThat(app.getCaseworker().getId()).isEqualTo(caseworkerId);

    String payload = "{ \"eventHistory\": { \"eventDescription\": \"Removing caseworker\" } }";

    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v0/applications/" + appId + "/unassign")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)
        ).andExpect(status().isOk());

    ApplicationEntity updated = applicationRepository.findById(appId).orElseThrow();
    assertThat(updated.getCaseworker()).isNull();
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReturn500IfDomainEventsMissing() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
            .username("caseworker_user")
            .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
            .status(ApplicationStatus.SUBMITTED)
            .applicationContent(Map.of("foo", "bar"))
            .createdAt(Instant.now())
            .modifiedAt(Instant.now())
            .build();

    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(applicationRepository.saveAndFlush(app).getId()))
            .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().is5xxServerError());
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationWriter"})
  @Transactional
  void shouldReturnOkIfEventsDescriptionMissing() throws Exception {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
            .username("caseworker_user")
            .build();
    UUID caseworkerId = caseworkerRepository.saveAndFlush(caseworker).getId();

    ApplicationEntity app = ApplicationEntity.builder()
            .status(ApplicationStatus.SUBMITTED)
            .applicationContent(Map.of("foo", "bar"))
            .createdAt(Instant.now())
            .modifiedAt(Instant.now())
            .build();

    CaseworkerAssignRequest request = CaseworkerAssignRequest.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(applicationRepository.saveAndFlush(app).getId()))
            .eventHistory(
              EventHistory.builder()
              .eventDescription(null)
              .build()
            )
            .build();
    String payload = objectMapper.writeValueAsString(request);

    mockMvc.perform(MockMvcRequestBuilders.post("/api/v0/applications/assign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk());
  }

}

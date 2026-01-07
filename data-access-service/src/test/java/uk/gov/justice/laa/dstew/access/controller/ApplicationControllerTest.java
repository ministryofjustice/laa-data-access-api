package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;

@WebMvcTest(
    controllers = ApplicationController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ResponseBodyAdvice.class),
    properties = {"feature.disable-security=true", "feature.disable-transformers=true"}
)
@ImportAutoConfiguration(
    exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    }
)
class ApplicationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  @MockitoBean
  private ApplicationService applicationService;

  @MockitoBean
  private ApplicationSummaryService applicationSummaryService;

  @MockitoBean
  private DomainEventService domainEventService;

//  @Test
//  void shouldCreateApplication() throws Exception {
//    UUID newId = UUID.randomUUID();
//    when(applicationService.createApplication(any())).thenReturn(newId);
//
//    String validRequestBody = """
//          {
//            "status": "SUBMITTED",
//            "schemaVersion": 1,
//            "applicationContent": { "foo": "bar" },
//            "laaReference": "laa_reference",
//            "individuals" : [
//              {
//                "firstName" : "John",
//                "lastName" : "Doe",
//                "dateOfBirth" : "2025-11-24",
//                "details" : { "foor" : "bar" }
//              }
//            ]
//          }
//        """;
//
//    var mvcResult = mockMvc.perform(
//            post("/api/v0/applications")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(validRequestBody)
//                .accept(MediaType.APPLICATION_JSON))
//        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
//        .andReturn();
//
//    String returnUri = mvcResult.getResponse().getHeader("Location");
//    assertThat(returnUri).isNotNull();
//    assertThat(returnUri).endsWith("/applications/" + newId);
//
//  }
//
//  @Test
//  void shouldUpdateItem() throws Exception {
//    UUID applicationId = UUID.randomUUID();
//    doNothing().when(applicationService).updateApplication(any(), any());
//    String validRequestBody = """
//          {
//            "status": "IN_PROGRESS",
//            "schemaVersion": 1,
//            "applicationContent": { "foo": "bar" }
//          }
//        """;
//
//    mockMvc.perform(
//            patch("/api/v0/applications/" + applicationId)
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(validRequestBody)
//                .accept(MediaType.APPLICATION_JSON))
//        .andExpect(status().isNoContent());
//  }
//
//  @Test
//  void shouldGetAllApplications() throws Exception {
//    ApplicationSummaryResponse applicationSummaryCollectionResponse = new ApplicationSummaryResponse();
//    List<ApplicationSummary> applications = new ArrayList<>();
//    applications.add(
//        ApplicationSummary.builder()
//            .applicationId(UUID.randomUUID())
//            .build());
//    applications.add(
//        ApplicationSummary.builder()
//            .applicationId(UUID.randomUUID())
//            .build());
//    applicationSummaryCollectionResponse.setApplications(applications);
//
//    when(applicationSummaryService.getAllApplications(any(), any(), any(),
//                                                      any(), any(), any(), any())).thenReturn(mock());
//    mockMvc
//        .perform(get("/api/v0/applications"))
//        .andExpect(status().isOk())
//        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
//
//  }
//
//  @Test
//  void shouldGetApplication() throws Exception {
//    OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
//    OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
//
//    var application = Application.builder()
//            .id(UUID.randomUUID())
//            .laaReference("app_reference")
//            .applicationContent(Map.of("foo", "bar"))
//            .createdAt(createdAt)
//            .updatedAt(updatedAt)
//            .build();
//
//    when(applicationService.getApplication(any())).thenReturn(application);
//
//    MvcResult result = mockMvc.perform(get("/api/v0/applications/" + application.getId()))
//        .andExpect(status().isOk())
//        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//        .andReturn();
//
//    Application resultApplication = deserialise(result, Application.class);
//
//    assertThat(resultApplication)
//            .usingRecursiveComparison()
//            .isEqualTo(application);
//  }
//
//  @Test
//  void shouldAssignCaseworker() throws Exception {
//    var validRequest =
//        """
//        {
//          "caseworkerId" : "f67e5290-c774-4e13-809b-37fc6cf9b09b",
//          "applicationIds" : [
//            "33703b45-f8b7-4143-8b5d-969826bdd090",
//            "8b92afd8-ab7b-4f5b-b0ea-2dcd7c2cde8f"
//          ]
//        }
//        """;
//
//    mockMvc.perform(post("/api/v0/applications/assign")
//                    .contentType("application/json")
//                    .content(validRequest))
//            .andExpect(status().isOk())
//            .andReturn();
//  }
//
//  @Test
//  void shouldUnassignCaseworker() throws Exception {
//    var validRequest =
//            """
//            {
//              "caseworkerId" : "f67e5290-c774-4e13-809b-37fc6cf9b09b",
//              "eventHistory" : {
//              "eventDescription":"removing caseworker"
//             }
//            }
//            """;
//
//    mockMvc.perform(post("/api/v0/applications/019a0c4c-92c6-7421-b62a-b6416e2a8402/unassign")
//                    .contentType("application/json")
//                    .content(validRequest))
//            .andExpect(status().isOk())
//            .andReturn();
//  }
//
//  @Test
//  void shouldGetApplicationHistory() throws Exception {
//    final UUID applicationId = UUID.randomUUID();
//
//    when(domainEventService.getEvents(applicationId, null))
//    .thenReturn(List.of(ApplicationDomainEvent.builder().build()));
//
//    String address = "/api/v0/applications/" + applicationId + "/history-search";
//
//    mockMvc.perform(get(address))
//    .andExpect(status().isOk())
//    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//    .andReturn();
//
//    verify(domainEventService, times(1)).getEvents(applicationId, null);
//  }
//
//
//  @Test
//  void shouldReturnNotFoundExceptionWithApplicationMessage() throws Exception {
//
//
//
//    UUID randomUuid = UUID.randomUUID();
//    when(applicationService.getApplication(randomUuid))
//            .thenThrow(new ResourceNotFoundException("Application not found"));
//    OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
//    OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
//    var application = Application.builder()
//            .id(randomUuid)
//            .laaReference("laa_reference")
//            .applicationContent(Map.of("foo", "bar"))
//            .createdAt(createdAt)
//            .updatedAt(updatedAt)
//            .build();
//    mockMvc.perform(get("/api/v0/applications/" + application.getId()))
//            .andExpect(status().is4xxClientError())
//            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
//            .andExpect(jsonPath("$.status").value(404))
//            .andExpect(jsonPath("$.detail").value("Application not found"))
//            .andExpect(jsonPath("$.title").value("Not Found"))
//            .andExpect(jsonPath("$.type").doesNotExist());
//
//  }
//
//  @Test
//  void shouldReturnNotFoundExceptionWithCaseworkerMessage() throws Exception {
//
//    UUID uuid = UUID.fromString("f67e5290-c774-4e13-809b-37fc6cf9b09b");
//    List<UUID> applicationIds = List.of(
//            UUID.fromString("33703b45-f8b7-4143-8b5d-969826bdd090"),
//            UUID.fromString("8b92afd8-ab7b-4f5b-b0ea-2dcd7c2cde8f"));
//    EventHistory eventHistory = new EventHistory();
//    eventHistory.setEventDescription("Caseworker assigned");
//    doThrow(new ResourceNotFoundException("Caseworker not found"))
//            .when(applicationService).assignCaseworker(uuid, applicationIds, eventHistory);
//
//    CaseworkerAssignRequest request = new CaseworkerAssignRequest();
//    request.setCaseworkerId(uuid);
//    request.setApplicationIds(applicationIds);
//    request.setEventHistory(eventHistory);
//    String requestJson = objectMapper.writeValueAsString(request);
//
//    mockMvc.perform(post("/api/v0/applications/assign")
//                    .contentType("application/json")
//                    .content(requestJson))
//            .andExpect(status().is4xxClientError())
//            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
//            .andExpect(jsonPath("$.status").value(404))
//            .andExpect(jsonPath("$.detail").value("Caseworker not found"))
//            .andExpect(jsonPath("$.title").value("Not Found"))
//            .andExpect(jsonPath("$.type").doesNotExist());
//
//  }
//
//  private <TResponseModel> TResponseModel deserialise(MvcResult result, Class<TResponseModel> clazz) throws Exception {
//    return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
//  }
}

package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;

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

  @MockitoBean
  private ApplicationService applicationService;

  @MockitoBean
  private ApplicationSummaryService applicationSummaryService;

  @Test
  void shouldCreateApplication() throws Exception {
    UUID newId = UUID.randomUUID();
    when(applicationService.createApplication(any())).thenReturn(newId);

    String validRequestBody = """
          {
            "status": "SUBMITTED",
            "schemaVersion": 1,
            "applicationContent": { "foo": "bar" },
            "applicationReference": "app_reference"
          }
        """;

    var mvcResult = mockMvc.perform(
            post("/api/v0/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
        .andReturn();

    String returnUri = mvcResult.getResponse().getHeader("Location");
    if (returnUri != null) {
      assertThat(returnUri).endsWith("/applications/" + newId);
    }
  }

  @Test
  void shouldUpdateItem() throws Exception {
    UUID applicationId = UUID.randomUUID();
    doNothing().when(applicationService).updateApplication(any(), any());

    mockMvc.perform(
            patch("/api/v0/applications/" + applicationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"application_content\": {\"status\":\"IN_PROGRESS\"}}")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldGetAllApplications() throws Exception {
    ApplicationSummaryResponse applicationSummaryCollectionResponse = new ApplicationSummaryResponse();
    List<ApplicationSummary> applications = new ArrayList<>();
    applications.add(
        ApplicationSummary.builder()
            .applicationId(UUID.randomUUID())
            .build());
    applications.add(
        ApplicationSummary.builder()
            .applicationId(UUID.randomUUID())
            .build());
    applicationSummaryCollectionResponse.setApplications(applications);

    when(applicationSummaryService.getAllApplications(any(), any(), any())).thenReturn(applications);
    mockMvc
        .perform(get("/api/v0/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));

  }

  @Test
  void shouldGetApplication() throws Exception {
    UUID appId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    when(applicationService.getApplication(any())).thenReturn(
        Application.builder()
            .id(appId)
            .applicationContent(Map.of("foo", "bar"))
            .build()
    );

    mockMvc.perform(get("/api/v0/applications/" + appId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(appId.toString()))
        .andExpect(jsonPath("$.applicationContent.foo").value("bar"));
  }
}

package uk.gov.justice.laa.dstew.access.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApplicationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ResponseBodyAdvice.class),
        properties = {"feature.disable-security=true", "feature.disable-transformers=true"})
@ImportAutoConfiguration(
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
public class ApplicationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    public void shouldCreateApplication() throws Exception {
        UUID newId = UUID.randomUUID();
        when(applicationService.createApplication(any())).thenReturn(newId);

        String returnUri = mockMvc
                .perform(
                        post("/api/v0/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider_firm_id\": \"firm-002\", \"provider_office_id\": \"office-201\"," +
                                        " \"client_id\": \"345e6789-eabb-34d5-a678-426614174333\"}")
                                .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(returnUri.endsWith("/applications/" + newId.toString())).isTrue();
    }

    @Test
    void shouldUpdateItem() throws Exception {

        ApplicationEntity applicationEntity = new ApplicationEntity();
        applicationEntity.setId(UUID.randomUUID());
        applicationEntity.setClientId(UUID.randomUUID());
        applicationEntity.setProviderOfficeId("office-002");

        doNothing().when(applicationService).updateApplication(any(), any());

        mockMvc
                .perform(
                        patch("/api/v0/applications/" + applicationEntity.getId().toString())
                                .content("{\"status_code\": \"IN_PROGRESS\"}")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetAllApplications() throws Exception {
        List<Application> applications = new ArrayList<>();
        applications.add(
                Application.builder()
                    .id(UUID.randomUUID())
                    .build());
        applications.add(
                Application.builder()
                    .id(UUID.randomUUID())
                    .build());

        when (applicationService.getAllApplications()).thenReturn(applications);
        mockMvc
                .perform(get("/api/v0/applications"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(2)));

    }

    @Test
    void shouldGetApplication() throws Exception {
        when (applicationService.getApplication(any())).thenReturn(
                Application.builder()
                    .id(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                    .clientId(UUID.randomUUID())
                    .statementOfCase("statement-of-case")
                    .build());

        mockMvc.perform(get("/api/v0/applications/123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(jsonPath("$.client_id").isNotEmpty())
                .andExpect(jsonPath("$.statement_of_case").isNotEmpty());
    }

    @Test
    void shouldDeleteApplication() throws Exception {
        mockMvc.perform(delete("/api/v0/applications/345e6789-eabb-34d5-a678-426614174333"))
                .andExpect(status().isNoContent());
    }
}

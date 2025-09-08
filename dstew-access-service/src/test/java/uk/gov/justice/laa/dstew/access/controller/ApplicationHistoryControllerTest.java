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
import uk.gov.justice.laa.dstew.access.model.ApplicationHistory;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApplicationHistoryController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ResponseBodyAdvice.class),
        properties = {"feature.disable-security=true", "feature.disable-transformers=true"})
@ImportAutoConfiguration(
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
public class ApplicationHistoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    void shouldGetAllApplicationHistory() throws Exception {
        UUID applicationId = UUID.randomUUID();
        List<ApplicationHistory> applicationHistory = new ArrayList<>();
            applicationHistory.add(
                ApplicationHistory.builder()
                        .applicationId(applicationId)
                        .id(UUID.randomUUID())
                        .build());
            applicationHistory.add(
                ApplicationHistory.builder()
                        .id(UUID.randomUUID())
                        .applicationId(applicationId)
                        .build());

        when (applicationService.getAllApplicationHistory(any())).thenReturn(applicationHistory);
        mockMvc
                .perform(get("/api/v0/applications/" + applicationId.toString() + "/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    void shouldGetLatestApplicationHistory() throws Exception {
        UUID applicationId = UUID.randomUUID();
        ApplicationHistory applicationHistory = ApplicationHistory.builder()
                                                .applicationId(applicationId)
                                                .id(UUID.randomUUID())
                                                .build();

        when (applicationService.getApplicationsLatestHistory(any())).thenReturn(applicationHistory);
        mockMvc
                .perform(get("/api/v0/applications/" + applicationId.toString() + "/history/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(applicationHistory.getId().toString()))
                .andExpect(jsonPath("$.application_id").value(applicationId.toString()));
    }
}

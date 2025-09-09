package uk.gov.justice.laa.dstew.access.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.service.DraftApplicationService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = DraftApplicationController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ResponseBodyAdvice.class),
        properties = {"feature.disable-security=true", "feature.disable-transformers=true"})
@ImportAutoConfiguration(
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
public class DraftApplicationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DraftApplicationService draftApplicationService;

    private String createDraftApplicationContent(boolean isBlankApplication) throws Exception {
        if (isBlankApplication) {
            return "{}";
        }

        return "{" +
                "\"provider_id\": \"79976a7e-a8f6-416a-8b95-370e983cd802\"," +
                "\"client_id\": \"1bb8028a-676d-4348-93b4-72987ad7b183\"" +
                "}";
    }

    @Test
    void shouldCreateItemWithBlankData() throws Exception {

        UUID newId = UUID.randomUUID();
        when(draftApplicationService.createApplication(any())).thenReturn(newId);

        String returnUri = mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createDraftApplicationContent(true))
                                .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(returnUri.endsWith("/draft-applications/" + newId.toString())).isTrue();
    }

    @Test
    void shouldCreateItem() throws Exception {

        UUID newId = UUID.randomUUID();
        when(draftApplicationService.createApplication(any())).thenReturn(newId);

        String returnUri = mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createDraftApplicationContent(false))
                                .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(returnUri.endsWith("/draft-applications/" + newId.toString())).isTrue();
    }

    @Test
    void shouldUpdateItem() throws Exception {

        DraftApplicationEntity draftApplicationEntity = new DraftApplicationEntity();
        draftApplicationEntity.setId(UUID.randomUUID());
        draftApplicationEntity.setClientId(UUID.randomUUID());
        draftApplicationEntity.setProviderId(UUID.randomUUID());

        when(draftApplicationService.updateApplication(eq(draftApplicationEntity.getId()), any())).thenReturn(draftApplicationEntity);

        mockMvc
                .perform(
                        patch("/api/v0/draft-applications/" + draftApplicationEntity.getId().toString())
                                .content(
                                    "{" +
                                    "\"id\": \"" + draftApplicationEntity.getId().toString() + "\"," +
                                    "\"provider_id\": \"" + draftApplicationEntity.getProviderId().toString() + "\"," +
                                    "\"client_id\": \"" + draftApplicationEntity.getClientId().toString() + "\"" +
                                    "}"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetItem() throws Exception {
        DraftApplication application = new DraftApplication();
        application.setId(UUID.randomUUID());
        application.setClientId(UUID.randomUUID());
        application.setProviderId(UUID.randomUUID());

        when(draftApplicationService.getApplicationById(eq(application.getId()))).thenReturn(application);

        mockMvc
            .perform(get("/api/v0/draft-applications/" + application.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("client_id").isNotEmpty())
            .andExpect(jsonPath("provider_id").isNotEmpty());
    }
}

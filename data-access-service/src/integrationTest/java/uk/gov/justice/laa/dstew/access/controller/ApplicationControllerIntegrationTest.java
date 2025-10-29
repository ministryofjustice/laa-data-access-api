package uk.gov.justice.laa.dstew.access.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.AccessApp;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=true")
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class ApplicationControllerIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void shouldGetAllApplications() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002",
                "provider_office_id": "office-201",
                "client_id": "4eae6789-eabb-34d5-a678-426614174643"
            }
        """;

        mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v0/applications"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(1)));
    }

    @Test
    void shouldGetApplicationById() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002",
                "provider_office_id": "office-201",
                "client_id": "345e6789-eabb-34d5-a678-426614174333"
            }
        """;

        String returnUri = mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(returnUri))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.provider_firm_id").value("firm-002"))
                .andExpect(jsonPath("$.client_id").value("345e6789-eabb-34d5-a678-426614174333"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void shouldCreateApplicationWithProceedings() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002",
                "provider_office_id": "office-201",
                "client_id": "345e6789-eabb-34d5-a678-426614174333",
                "proceedings": [
                    {
                        "proceeding_code": "EPO_EXTEND",
                        "level_of_service_code": "FULL"
                    }
                ]
            }
        """;

        String returnUri = mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(returnUri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceedings[0].proceeding_code").value("EPO_EXTEND"))
                .andExpect(jsonPath("$.proceedings[0].level_of_service_code").value("FULL"));
    }

    @Test
    void shouldUpdateApplicationAndProceedings() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002",
                "provider_office_id": "office-201",
                "client_id": "345e6789-eabb-34d5-a678-426614174333",
                "proceedings": [
                    {
                        "proceeding_code": "EPO_EXTEND",
                        "level_of_service_code": "FULL"
                    }
                ]
            }
        """;

        String returnUri = mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String patchPayload = """
            {
                "status_code": "IN_PROGRESS",
                "proceedings": [
                    {
                        "proceeding_code": "EPO_EXTEND",
                        "level_of_service_code": "LIMITED"
                    }
                ]
            }
        """;

        mockMvc.perform(patch(returnUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(returnUri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_code").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.proceedings[0].level_of_service_code").value("LIMITED"));
    }

    @Test
    void shouldDeleteApplication() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002",
                "provider_office_id": "office-201",
                "client_id": "345e6789-eabb-34d5-a678-426614174333"
            }
        """;

        String returnUri = mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(delete(returnUri))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnBadRequestForMissingRequiredFields() throws Exception {
        String payload = """
            {
                "provider_firm_id": "firm-002"
            }
        """;

        mockMvc.perform(post("/api/v0/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}

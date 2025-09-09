package uk.gov.justice.laa.dstew.access.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.AccessApp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=true")
@AutoConfigureMockMvc
@Transactional
public class DraftApplicationControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateEmptyItem() throws Exception {
        mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldCreateItemWithIdOnly() throws Exception {
        mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider_id\": \"79976a7e-a8f6-416a-8b95-370e983cd802\"," +
                                        " \"client_id\": \"1bb8028a-676d-4348-93b4-72987ad7b183\"}")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldCreateItem() throws Exception {
        mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"additional_data\": {" +
                                        "\"keyA\": \"valueA\"," +
                                        "\"keyB\": \"valueB\"," +
                                        "\"keyD\": {" +
                                        "\"keyDA\": \"valueDA\"," +
                                        "\"keyDB\": \"valueDB\"" +
                                        "}" +
                                        "}," +
                                        "\"provider_id\": \"79976a7e-a8f6-416a-8b95-370e983cd802\"," +
                                        "\"client_id\": \"1bb8028a-676d-4348-93b4-72987ad7b183\"}")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldUpdateItem() throws Exception {
        String returnUri = mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider_id\": \"79976a7e-a8f6-416a-8b95-370e983cd802\"," +
                                        " \"client_id\": \"1bb8028a-676d-4348-93b4-72987ad7b183\"}")
                                .accept(MediaType.APPLICATION_JSON))
                                .andReturn()
                                .getResponse()
                                .getHeader("Location");

        mockMvc
                .perform(
                        patch(returnUri)
                                .content("{\"provider_id\": \"09976a7e-a8f6-416a-8b95-370e983cd802\"," +
                                        " \"client_id\": \"0bb8028a-676d-4348-93b4-72987ad7b183\"}")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetItem() throws Exception {
        String returnUri = mockMvc
                .perform(
                        post("/api/v0/draft-applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider_id\": \"29976a7e-a8f6-416a-8b95-370e983cd802\"," +
                                        " \"client_id\": \"2bb8028a-676d-4348-93b4-72987ad7b183\"}")
                                .accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(returnUri))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("client_id").isNotEmpty())
                .andExpect(jsonPath("provider_id").isNotEmpty());
    }
}

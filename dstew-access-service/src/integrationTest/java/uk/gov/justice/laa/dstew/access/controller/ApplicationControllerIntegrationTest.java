package uk.gov.justice.laa.dstew.access.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.AccessApp;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=true")
@AutoConfigureMockMvc
@Transactional
@Disabled
public class ApplicationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldGetAllItems() throws Exception {
    mockMvc
            .perform(
                    post("/api/v0/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider_firm_id\": \"firm-002\", \"provider_office_id\": \"office-201\"," +
                                    " \"client_id\": \"4eae6789-eabb-34d5-a678-426614174643\"}")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());

    mockMvc
            .perform(get("/api/v0/applications"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)));
  }

  @Test
  void shouldGetItem() throws Exception {
    mockMvc.perform(get("/api/v0/applications/123e4567-e89b-12d3-a456-426614174000"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("123e4567-e89b-12d3-a456-426614174000"))
            .andExpect(jsonPath("$.client_id").isNotEmpty())
            .andExpect(jsonPath("$.statement_of_case").isNotEmpty());
  }

  @Test
  void shouldCreateItem() throws Exception {
    mockMvc
            .perform(
                    post("/api/v0/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider_firm_id\": \"firm-002\", \"provider_office_id\": \"office-201\"," +
                                    " \"client_id\": \"345e6789-eabb-34d5-a678-426614174333\"}")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
  }

  @Test
  void shouldUpdateItem() throws Exception {
    mockMvc
            .perform(
                    patch("/api/v0/applications/123e4567-e89b-12d3-a456-426614174000")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status_code\": \"IN_PROGRESS\"}")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
  }

  @Test
  void shouldDeleteItem() throws Exception {
    mockMvc.perform(delete("/api/v0/applications/345e6789-eabb-34d5-a678-426614174333")).andExpect(status().isNoContent());
  }
}
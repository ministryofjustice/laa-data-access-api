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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.AccessApp;

@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=true")
@AutoConfigureMockMvc
@Transactional
public class ApplicationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldCreateItem() throws Exception {
    String request = "{" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"application\": {" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"client\": {" +
            "\"individualId\": \"individualId\"," +
            "\"employmentStatusCode\": \"employmentStatusCode\"" +
            "}" +
            "}" +
            "}";

    mockMvc
            .perform(
                    post("/api/v2/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
  }

  @Test
  void shouldUpdateItem() throws Exception {
    String createRequest = "{" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"application\": {" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"client\": {" +
            "\"individualId\": \"individualId\"," +
            "\"employmentStatusCode\": \"employmentStatusCode\"" +
            "}" +
            "}" +
            "}";

    String returnUri = mockMvc
            .perform(
                    post("/api/v2/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequest)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

    String updateRequest = "{" +
            "\"providerOfficeId\": \"office-202\"" +
            "}";
    mockMvc
            .perform(
                    patch(returnUri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequest)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
  }

  @Test
  void shouldGetAllItems() throws Exception {
    String createRequest = "{" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"application\": {" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"client\": {" +
            "\"individualId\": \"individualId\"," +
            "\"employmentStatusCode\": \"employmentStatusCode\"" +
            "}" +
            "}" +
            "}";

    mockMvc
            .perform(
                    post("/api/v2/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequest)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());

    mockMvc
            .perform(get("/api/v2/applications"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(1)));
  }

  @Test
  void shouldGetItem() throws Exception {
    String createRequest = "{" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"application\": {" +
            "\"providerOfficeId\": \"office-201\"," +
            "\"client\": {" +
            "\"individualId\": \"individualId\"," +
            "\"employmentStatusCode\": \"employmentStatusCode\"" +
            "}" +
            "}" +
            "}";

    String returnUri = mockMvc
            .perform(
                    post("/api/v2/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequest)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

    mockMvc.perform(get(returnUri))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("123e4567-e89b-12d3-a456-426614174000"))
            .andExpect(jsonPath("$.client_id").isNotEmpty())
            .andExpect(jsonPath("$.statement_of_case").isNotEmpty());
  }

  @Test
  void shouldDeleteItem() throws Exception {
    String createRequest = "{" +
            "\"application\": {" +
            "\"providerOfficeId\": \"office-401\"," +
            "\"client\": {" +
            "\"individualId\": \"individualId\"," +
            "\"employmentStatusCode\": \"employmentStatusCode\"" +
            "}" +
            "}" +
            "}";

    String returnUri = mockMvc
            .perform(
                    post("/api/v2/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequest)
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");

    String updateRequest = "{" +
            "\"providerOfficeId\": \"office-202\"" +
            "}";
    mockMvc.perform(delete(returnUri)).andExpect(status().isNoContent());
  }
}

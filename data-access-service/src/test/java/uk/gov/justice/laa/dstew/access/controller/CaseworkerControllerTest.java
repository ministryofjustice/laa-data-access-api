package uk.gov.justice.laa.dstew.access.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.service.CaseworkerService;

import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;

@WebMvcTest(
    controllers = CaseworkerController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ResponseBodyAdvice.class),
    properties = {"feature.disable-security=true", "feature.disable-transformers=true"}
)
@ImportAutoConfiguration(
    exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    }
)
public class CaseworkerControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private CaseworkerService caseworkerService;

    @Test
    void getCaseworkers() throws Exception {
        var caseworkers = List.of(Caseworker.builder()
                                            .id(UUID.randomUUID())
                                            .username("caseworker1")
                                            .build(), 
                                  Caseworker.builder()
                                            .id(UUID.randomUUID())
                                            .username("caseworker2")
                                            .build());
        String expectedJson = objectMapper.writeValueAsString(caseworkers);
        when(caseworkerService.getAllCaseworkers()).thenReturn(caseworkers);

        mockMvc.perform(get("/api/v0/caseworkers"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(expectedJson));
    }
    
}

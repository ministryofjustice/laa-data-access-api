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


}

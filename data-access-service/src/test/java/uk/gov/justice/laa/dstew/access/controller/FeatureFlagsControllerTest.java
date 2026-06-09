package uk.gov.justice.laa.dstew.access.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;

@SpringBootTest(
    properties = {
      "feature.disable-jpa-auditing=true",
      "feature.disable-security=true",
      "flagd.enabled=false"
    })
@AutoConfigureMockMvc
@ImportAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
    })
class FeatureFlagsControllerTest {

  @Autowired private MockMvc mockMvc;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private ApplicationRepository applicationRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private CaseworkerRepository caseworkerRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private DomainEventRepository domainEventRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private CertificateRepository certificateRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private IndividualRepository individualRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private NoteRepository noteRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private LinkedApplicationRepository linkedApplicationRepository;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private ProceedingRepository proceedingRepository;

  @Test
  void returnsDefaultFlagValuesWhenFlagdIsDisabled() throws Exception {
    mockMvc
        .perform(get("/flags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pocEnabled").value(false))
        .andExpect(jsonPath("$.pocVariant").value("control"));
  }
}




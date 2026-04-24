package uk.gov.justice.laa.dstew.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.massgenerator.MassDataGeneratorService;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.LinkedIndividualWriter;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.repository.*;

@SpringBootTest(properties = {"feature.disable-jpa-auditing=true", "feature.disable-security=true"})
@ImportAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
    })
public class AccessAppTests {
  @MockitoBean private ApplicationRepository applicationRepository;

  @MockitoBean private ApplicationSummaryRepository applicationSummaryRepository;

  @MockitoBean private CaseworkerRepository caseworkerRepository;

  @MockitoBean private DomainEventRepository domainEventRepository;

  @MockitoBean private DecisionRepository decisionRepository;

  @MockitoBean private ProceedingRepository proceedingRepository;

  @MockitoBean private MeritsDecisionRepository meritsDecisionRepository;

  @MockitoBean private CertificateRepository certificateRepository;

  @MockitoBean private IndividualRepository individualRepository;

  @MockitoBean private NoteRepository noteRepository;

  @MockitoBean private MassDataGeneratorService massDataGeneratorService;

  @MockitoBean(name = "massGeneratorPersistedDataGenerator")
  private PersistedDataGenerator persistedDataGenerator;

  @MockitoBean(name = "massGeneratorLinkedIndividualWriter")
  private LinkedIndividualWriter linkedIndividualWriter;

  @Test
  void contextLoads() {}
}

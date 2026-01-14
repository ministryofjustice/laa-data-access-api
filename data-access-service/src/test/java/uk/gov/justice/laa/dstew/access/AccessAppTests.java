package uk.gov.justice.laa.dstew.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.*;

@SpringBootTest(properties = {"feature.disable-jpa-auditing=true", "feature.disable-security=true"})
@ImportAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
})
public class AccessAppTests {
  @MockitoBean
  private ApplicationRepository applicationRepository;

  @MockitoBean
  private ApplicationSummaryRepository applicationSummaryRepository;

  @MockitoBean
  private CaseworkerRepository caseworkerRepository;

  @MockitoBean
  private DomainEventRepository domainEventRepository;

  @MockitoBean
  private DecisionRepository decisionRepository;

  @Test
  void contextLoads() {
  }
}

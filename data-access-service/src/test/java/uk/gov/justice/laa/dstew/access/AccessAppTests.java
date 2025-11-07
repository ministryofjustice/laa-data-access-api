package uk.gov.justice.laa.dstew.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

// `feature.security=false` prevents Entra auto-configuration failing context initialization
// (temporary until figure out a better solution).
@SpringBootTest(properties = {"feature.disable-jpa-auditing=true", "feature.disable-security=true"})
@ImportAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
})
class AccessAppTests {
  @MockitoBean
  private ApplicationRepository applicationRepository;

  @MockitoBean
  private ApplicationSummaryRepository applicationSummaryRepository;

  @Test
  void contextLoads() {
  }
}

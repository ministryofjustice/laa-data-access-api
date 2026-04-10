package uk.gov.justice.laa.dstew.access.utils;

import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.repository.*;

@SpringBootTest(
    properties = {"feature.disable-jpa-auditing=true", "feature.disable-security=false"})
@ImportAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
    })
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("unit-test")
public class BaseServiceTest {

  @MockitoBean protected ApplicationRepository applicationRepository;

  @MockitoBean protected DomainEventRepository domainEventRepository;

  @MockitoBean protected CaseworkerRepository caseworkerRepository;

  @MockitoBean protected ApplicationSummaryRepository applicationSummaryRepository;

  @MockitoBean protected ProceedingRepository proceedingRepository;

  @MockitoBean protected CertificateRepository certificateRepository;

  @MockitoBean protected IndividualRepository individualRepository;

  @MockitoBean protected NoteRepository noteRepository;

  @MockitoBean protected ServiceNameContext serviceNameContext;

  @Autowired protected ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(serviceNameContext.getServiceName()).thenReturn(ServiceName.CIVIL_APPLY);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  protected void setSecurityContext(String[] roles) {
    var authorities = Stream.of(roles).map(SimpleGrantedAuthority::new).toList();

    var authentication = new TestingAuthenticationToken("user", "password", authorities);
    authentication.setAuthenticated(true);

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  protected void setSecurityContext(String role) {
    setSecurityContext(new String[] {role});
  }
}

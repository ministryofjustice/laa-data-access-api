package uk.gov.justice.laa.dstew.access.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationContentFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationSummaryFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ProceedingDetailsFactory;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.application.*;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.domainEvent.DomainEventFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactory;

import java.util.stream.Stream;

@SpringBootTest(properties = {"feature.disable-jpa-auditing=true", "feature.disable-security=false"})
@ImportAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
})
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("unit-test")
public class BaseServiceTest {

    @MockitoBean
    protected ApplicationRepository applicationRepository;

    @MockitoBean
    protected DomainEventRepository domainEventRepository;

    @MockitoBean
    protected CaseworkerRepository caseworkerRepository;

    @MockitoBean
    protected ApplicationSummaryRepository applicationSummaryRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ApplicationEntityFactory applicationEntityFactory;

    @Autowired
    protected ApplicationCreateRequestFactory applicationCreateRequestFactory;

    @Autowired
    protected ApplicationUpdateRequestFactory applicationUpdateRequestFactory;

    @Autowired
    protected ApplicationSummaryFactory applicationSummaryEntityFactory;

    @Autowired
    protected IndividualFactory individualFactory;

    @Autowired
    protected CaseworkerFactory caseworkerFactory;

    @Autowired
    protected DomainEventFactory domainEventFactory;

    @Autowired
    protected ApplicationContentFactory applicationContentFactory;

    @Autowired
    protected ProceedingDetailsFactory proceedingDetailsFactory;

    @Autowired
    protected ApplicationAssignDecisionRequestFactory applicationAssignDecisionRequestFactory;

    @MockitoBean
    protected DecisionRepository decisionRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    protected void setSecurityContext(String[] roles) {
        var authorities = Stream.of(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new TestingAuthenticationToken("user", "password", authorities);
        authentication.setAuthenticated(true);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    protected void setSecurityContext(String role) {
        setSecurityContext(new String[] {role});
    }
}

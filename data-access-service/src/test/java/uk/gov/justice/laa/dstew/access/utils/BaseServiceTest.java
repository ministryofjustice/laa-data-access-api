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
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationContentFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationMakeDecisionRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationSummaryFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateRequestFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.decision.DecisionEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.domainEvent.DomainEventFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionDetailsFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionsEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.MakeDecisionProceedingFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingsEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.refusal.RefusalDetailsFactory;

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

    @MockitoBean
    protected ProceedingRepository proceedingRepository;

    @MockitoBean
    protected DecisionRepository decisionRepository;

    @MockitoBean
    protected MeritsDecisionRepository meritsDecisionRepository;

    @MockitoBean
    protected IndividualRepository individualRepository;

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
    protected ApplicationMakeDecisionRequestFactory applicationMakeDecisionRequestFactory;

    @Autowired
    protected DecisionEntityFactory decisionEntityFactory;

    @Autowired
    protected MakeDecisionProceedingFactory makeDecisionProceedingFactory;

    @Autowired
    protected MeritsDecisionDetailsFactory meritsDecisionDetailsFactory;

    @Autowired
    protected MeritsDecisionsEntityFactory meritsDecisionsEntityFactory;

    @Autowired
    protected ProceedingsEntityFactory proceedingsEntityFactory;

    @Autowired
    protected RefusalDetailsFactory refusalDetailsFactory;

    @Autowired
    protected ProceedingFactory proceedingFactory;

    @Autowired
    protected IndividualEntityFactory individualEntityFactory;

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

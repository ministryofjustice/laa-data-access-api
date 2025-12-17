package uk.gov.justice.laa.dstew.access.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

import java.util.List;
import java.util.stream.Stream;

@SpringBootTest(properties = {"feature.disable-jpa-auditing=true"})
@ImportAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
})
@ExtendWith(MockitoExtension.class)
public class BaseServiceTest {

    @MockitoBean
    protected ApplicationRepository applicationRepository;

    @MockitoBean
    protected DomainEventRepository domainEventRepository;

    @MockitoBean
    protected CaseworkerRepository caseworkerRepository;

    @MockitoBean
    protected ApplicationSummaryRepository applicationSummaryRepository;

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

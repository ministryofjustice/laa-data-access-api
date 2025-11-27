package uk.gov.justice.laa.dstew.access.utils.doubles;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.justice.laa.dstew.access.config.SecurityConfig;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;
import uk.gov.justice.laa.dstew.access.utils.TestSpringContext;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

import java.util.List;
import java.util.stream.Stream;

public class ApplicationServiceDouble {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private String[] roles = new String[] {};
    private ApplicationRepository applicationRepository = Mockito.mock(ApplicationRepository.class);

    public ApplicationServiceDouble withRepository(ApplicationRepository repository) {
        this.applicationRepository = repository;
        return this;
    }

    public ApplicationServiceDouble withRoles(String... roles) {
        this.roles = Stream.concat(Stream.of(this.roles), Stream.of(roles)).toArray(String[]::new);
        return this;
    }

    public ApplicationService build() {

        TestSpringContext ctx = new TestSpringContext()
                .withBean(ApplicationRepository.class, applicationRepository)
                .withBean(ApplicationMapper.class, Mappers.getMapper(ApplicationMapper.class))
                .withBean("entra", EffectiveAuthorizationProvider.class, securityConfig.authProvider())
                .withBean(ObjectMapper.class, new ObjectMapper())
                .withConfig(TestSecurityConfig.class)
                .constructBeans(ApplicationValidations.class)
                .constructBeans(ApplicationService.class)
                .build();

        setSecurityContext();
        return ctx.getBean(ApplicationService.class);
    }

    // TODO: check that this works with tests in parallel..
    private void setSecurityContext() {
        var authorities = List.of(this.roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new TestingAuthenticationToken("user", "password", authorities);
        authentication.setAuthenticated(true);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
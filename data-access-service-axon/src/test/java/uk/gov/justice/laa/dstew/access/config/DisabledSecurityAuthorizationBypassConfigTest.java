package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-disabled-security;DB_CLOSE_DELAY=-1",
      "feature.disable-security=true",
      "ENTRA_ISSUER_URI=" + TestJwtDecoderConfig.ISSUER_URI,
      "ENTRA_AUD=" + TestJwtDecoderConfig.AUDIENCE
    })
class DisabledSecurityAuthorizationBypassConfigTest {

  @Autowired
  @Qualifier("entra")
  private EffectiveAuthorizationProvider authProvider;

  @Test
  void givenDisabledSecurity_whenHasAppRole_thenReturnsTrue() {
    assertThat(authProvider.hasAppRole("ANY_ROLE")).isTrue();
  }

  @Test
  void givenDisabledSecurity_whenHasAnyAppRole_thenReturnsTrue() {
    assertThat(authProvider.hasAnyAppRole("ROLE_A", "ROLE_B")).isTrue();
  }

  @Test
  void givenDisabledSecurity_whenHasName_thenReturnsTrue() {
    assertThat(authProvider.hasName()).isTrue();
  }
}

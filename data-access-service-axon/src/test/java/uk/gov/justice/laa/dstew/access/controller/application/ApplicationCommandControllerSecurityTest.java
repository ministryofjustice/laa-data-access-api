package uk.gov.justice.laa.dstew.access.controller.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.document.UploadDocumentUseCase;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.config.SecurityConfig;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtDecoder;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtToken;

@SpringBootTest(
    classes = ApplicationCommandControllerSecurityTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "feature.disable-security=false",
      "ENTRA_ISSUER_URI=https://issuer.example.test",
      "ENTRA_JWK_SET_URI=https://issuer.example.test/jwks",
      "ENTRA_AUD=api://data-access-api-test"
    })
@AutoConfigureTestRestTemplate
class ApplicationCommandControllerSecurityTest {

  private static final String UNKNOWN_ROLE_BEARER_TOKEN = "unknown-role-token";

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockitoBean private RetryingCommandDispatcher dispatcher;
  @MockitoBean private SubscriptionProjectionGateway projectionGateway;
  @MockitoBean private MakeApplicationDecisionUseCase makeDecisionUseCase;
  @MockitoBean private CreateNoteUseCase createNoteUseCase;
  @MockitoBean private UnassignCaseworkerUseCase unassignCaseworkerUseCase;
  @MockitoBean private AssignCaseworkerUseCase assignCaseworkerUseCase;
  @MockitoBean private RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  @MockitoBean private UpdateApplicationUseCase updateApplicationUseCase;
  @MockitoBean private UploadDocumentUseCase uploadDocumentUseCase;
  @MockitoBean private CreatePriorAuthorityUseCase createPriorAuthorityUseCase;
  @MockitoBean private CreateApplicationCommandMapper commandMapper;
  @MockitoBean private MakeDecisionCommandMapper decisionCommandMapper;
  @MockitoBean private AssignCaseworkerRequestMapper assignCaseworkerRequestMapper;
  @MockitoBean private UnassignCaseworkerRequestMapper unassignCaseworkerRequestMapper;
  @MockitoBean private CreateNoteCommandMapper createNoteCommandMapper;
  @MockitoBean private AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  @MockitoBean private UpdateApplicationCommandMapper updateApplicationCommandMapper;
  @MockitoBean private CreatePriorAuthorityCommandMapper createPriorAuthorityCommandMapper;

  @Test
  void givenNoCredentials_whenCreateApplication_thenReturnsUnauthorized() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.exchange(
            url(), HttpMethod.POST, new HttpEntity<>(validRequest(), headers), String.class);

    org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    verifyNoInteractions(commandMapper, dispatcher, projectionGateway);
  }

  @Test
  void givenAuthenticatedUserWithoutCaseworkerRole_whenCreateApplication_thenReturnsAccepted() {
    when(commandMapper.toCommand(any(), anyInt())).thenReturn(validCommand());

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(UNKNOWN_ROLE_BEARER_TOKEN);

    var response =
        restTemplate.exchange(
            url(), HttpMethod.POST, new HttpEntity<>(validRequest(), headers), String.class);

    org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);

    verifyNoInteractions(dispatcher);
  }

  private CreateApplicationCommand validCommand() {
    UUID applicationId = UUID.randomUUID();
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        validRequest().getApplicationContent(),
        "{}",
        1,
        "BaseCivilApplication.json");
  }

  private uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest validRequest() {
    return validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
  }

  private String url() {
    return "http://localhost:" + port + "/api/v0/applications";
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  @Import({
    ApplicationCommandController.class,
    CreateApplicationUseCase.class,
    SecurityConfig.class,
    UnknownRoleJwtConfig.class
  })
  static class TestApplication {}

  @TestConfiguration
  static class UnknownRoleJwtConfig {

    @Bean
    @Primary
    JwtDecoder stubJwtDecoder() {
      return StubJwtDecoder.of(
          new StubJwtToken(
              UNKNOWN_ROLE_BEARER_TOKEN,
              "unknown@example.com",
              new String[] {"UNKNOWN"},
              null,
              Map.of(
                  "iss",
                  "https://issuer.example.test",
                  "aud",
                  List.of("api://data-access-api-test"))));
    }
  }
}

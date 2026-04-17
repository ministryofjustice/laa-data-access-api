package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreateApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private ProceedingGateway proceedingGateway;
  @Mock private DomainEventGateway domainEventGateway;

  private ApplicationContentParserService contentParser;
  private CreateApplicationUseCase useCase;
  private ObjectMapper objectMapper;

  private static final Map<String, Object> VALID_CONTENT =
      Map.of(
          "id",
          UUID.randomUUID().toString(),
          "submittedAt",
          "2024-01-01T12:00:00Z",
          "proceedings",
          List.of(
              Map.of(
                  "id", UUID.randomUUID().toString(),
                  "leadProceeding", true,
                  "description", "Test proceeding",
                  "categoryOfLaw", "FAMILY",
                  "matterType", "SPECIAL_CHILDREN_ACT")));

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    contentParser = new ApplicationContentParserService(objectMapper);
    useCase =
        new CreateApplicationUseCase(
            applicationGateway, proceedingGateway, domainEventGateway, contentParser, objectMapper);
  }

  @Test
  void execute_happyPath_returnsSavedId() {
    UUID savedId = UUID.randomUUID();
    ApplicationDomain saved = buildSavedDomain(savedId);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    CreateApplicationCommand command =
        new CreateApplicationCommand(
            ApplicationStatus.APPLICATION_IN_PROGRESS, "REF001", VALID_CONTENT, List.of());

    UUID result = useCase.execute(command);

    assertThat(result).isEqualTo(savedId);
    verify(applicationGateway).save(any());
    verify(domainEventGateway).saveCreatedEvent(saved);
  }

  @Test
  void execute_duplicate_throwsValidationException() {
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(true);

    CreateApplicationCommand command =
        new CreateApplicationCommand(
            ApplicationStatus.APPLICATION_IN_PROGRESS, "REF001", VALID_CONTENT, List.of());

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).errors())
        .satisfies(
            errors ->
                assertThat((java.util.List<?>) errors)
                    .anyMatch(e -> e.toString().contains("Application already exists")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void execute_missingLeadApplication_throwsResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID assocId = UUID.randomUUID();
    Map<String, Object> contentWithLink =
        Map.of(
            "id",
            assocId.toString(),
            "submittedAt",
            "2024-01-01T12:00:00Z",
            "allLinkedApplications",
            List.of(
                Map.of(
                    "leadApplicationId", leadApplyId.toString(),
                    "associatedApplicationId", assocId.toString())),
            "proceedings",
            List.of(
                Map.of(
                    "id", UUID.randomUUID().toString(),
                    "leadProceeding", true,
                    "description", "Test",
                    "categoryOfLaw", "FAMILY",
                    "matterType", "SPECIAL_CHILDREN_ACT")));

    UUID savedId = UUID.randomUUID();
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(buildSavedDomain(savedId));
    when(applicationGateway.findByApplyApplicationId(leadApplyId)).thenReturn(Optional.empty());

    CreateApplicationCommand command =
        new CreateApplicationCommand(
            ApplicationStatus.APPLICATION_IN_PROGRESS, "REF001", contentWithLink, List.of());

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Lead application not found");
  }

  @Test
  void execute_contentWithNoLeadProceeding_throwsValidationException() {
    Map<String, Object> badContent =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2024-01-01T12:00:00Z",
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "leadProceeding",
                    false,
                    "description",
                    "Non-lead")));

    CreateApplicationCommand command =
        new CreateApplicationCommand(
            ApplicationStatus.APPLICATION_IN_PROGRESS, "REF001", badContent, List.of());

    assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_isAnnotatedWithCorrectRole() throws NoSuchMethodException {
    var method =
        CreateApplicationUseCase.class.getMethod("execute", CreateApplicationCommand.class);
    var annotation = method.getAnnotation(EnforceRole.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.anyOf()).contains(RequiredRole.API_CASEWORKER);
  }

  private ApplicationDomain buildSavedDomain(UUID id) {
    return new ApplicationDomain(
        id,
        ApplicationStatus.APPLICATION_IN_PROGRESS,
        "REF001",
        "OFFICE1",
        UUID.randomUUID(),
        false,
        uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw.FAMILY,
        uk.gov.justice.laa.dstew.access.domain.MatterType.SPECIAL_CHILDREN_ACT,
        Instant.parse("2024-01-01T12:00:00Z"),
        Instant.now(),
        VALID_CONTENT,
        List.of(),
        1);
  }
}

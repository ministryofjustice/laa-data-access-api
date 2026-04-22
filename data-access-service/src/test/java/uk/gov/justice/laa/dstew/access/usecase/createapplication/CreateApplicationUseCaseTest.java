package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
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
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.CreateApplicationCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.DomainLinkedApplicationsGenerator;
import uk.gov.justice.laa.dstew.access.validation.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreateApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private ProceedingGateway proceedingGateway;
  @Mock private DomainEventGateway domainEventGateway;

  private ApplicationContentParserService contentParser;
  private CreateApplicationUseCase useCase;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    contentParser =
        new ApplicationContentParserService(
            objectMapper, new PayloadValidationService(objectMapper, validator));
    useCase =
        new CreateApplicationUseCase(
            applicationGateway, proceedingGateway, domainEventGateway, contentParser, objectMapper);
  }

  @Test
  void execute_happyPath_returnsSavedId() {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    UUID expectedId = saved.id();
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

    UUID result = useCase.execute(command);

    assertThat(result).isEqualTo(expectedId);
    verify(applicationGateway).save(any());
    verify(domainEventGateway).saveCreatedEvent(saved, command.serialisedRequest());
  }

  @Test
  void execute_happyPath_proceedingsSaved() {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

    useCase.execute(command);

    verify(proceedingGateway).saveAll(any(), any());
  }

  @Test
  void execute_withLinkedApplication_callsAddLinkedApplication() {
    UUID leadApplyId = UUID.randomUUID();
    UUID assocId = UUID.randomUUID();

    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    ApplicationDomain lead = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);
    when(applicationGateway.findByApplyApplicationId(leadApplyId)).thenReturn(Optional.of(lead));
    when(applicationGateway.addLinkedApplication(any(), any())).thenReturn(lead);

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(linkedContent(leadApplyId, assocId)));

    useCase.execute(command);

    verify(applicationGateway).addLinkedApplication(lead, saved);
  }

  @Test
  void execute_duplicate_throwsValidationException() {
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(true);

    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

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
  void execute_missingAssociatedApplication_throwsResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID assocId = UUID.randomUUID();
    UUID missingId = UUID.randomUUID();

    // two linked apps: the one being created (assocId) plus one that doesn't exist (missingId)
    var contentWithMissingAssoc =
        objectMapper.convertValue(
            DataGenerator.createDefault(
                ApplicationContentDomainGenerator.class,
                ac ->
                    ac.id(assocId)
                        .allLinkedApplications(
                            List.of(
                                DataGenerator.createDefault(
                                    DomainLinkedApplicationsGenerator.class,
                                    b ->
                                        b.leadApplicationId(leadApplyId)
                                            .associatedApplicationId(assocId)),
                                DataGenerator.createDefault(
                                    DomainLinkedApplicationsGenerator.class,
                                    b ->
                                        b.leadApplicationId(leadApplyId)
                                            .associatedApplicationId(missingId))))),
            Map.class);

    // assocId is the application being created — its existence check is filtered out
    // missingId does not exist
    when(applicationGateway.existsByApplyApplicationId(assocId)).thenReturn(false); // new app
    when(applicationGateway.existsByApplyApplicationId(missingId)).thenReturn(false);

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(contentWithMissingAssoc));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No linked application found with associated apply ids:");

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void execute_missingLeadApplication_throwsResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID assocId = UUID.randomUUID();

    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any()))
        .thenReturn(DataGenerator.createDefault(ApplicationDomainGenerator.class));
    when(applicationGateway.findByApplyApplicationId(leadApplyId)).thenReturn(Optional.empty());

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(linkedContent(leadApplyId, assocId)));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Lead application not found");
  }

  @Test
  void execute_contentWithNoLeadProceeding_throwsValidationException() {
    var noLeadProceedingContent =
        objectMapper.convertValue(
            DataGenerator.createDefault(
                ApplicationContentDomainGenerator.class,
                ac ->
                    ac.proceedings(
                        List.of(
                            DataGenerator.createDefault(
                                uk.gov.justice.laa.dstew.access.utils.generator.proceeding
                                    .DomainProceedingGenerator.class,
                                p -> p.leadProceeding(false))))),
            Map.class);

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(noLeadProceedingContent));

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

  // ── Helpers ────────────────────────────────────────────────────────────────

  private Map<String, Object> linkedContent(UUID leadApplyId, UUID assocId) {
    var link =
        DataGenerator.createDefault(
            DomainLinkedApplicationsGenerator.class,
            b -> b.leadApplicationId(leadApplyId).associatedApplicationId(assocId));
    return objectMapper.convertValue(
        DataGenerator.createDefault(
            ApplicationContentDomainGenerator.class,
            ac -> ac.id(assocId).allLinkedApplications(List.of(link))),
        Map.class);
  }
}

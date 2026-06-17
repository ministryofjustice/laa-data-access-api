package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.LinkedApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.LinkedApplication;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.createapplication.CreateApplicationCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreateApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private LinkedApplicationGateway linkedApplicationGateway;
  @Mock private SaveDomainEventService saveDomainEventService;

  private CreateApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    // Real (not mocked) instances — preserves bean validation behaviour
    PayloadValidationService validationService =
        new PayloadValidationService(
            MapperUtil.getObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
    ApplicationContentParser parser = new ApplicationContentParser(validationService);

    useCase =
        new CreateApplicationUseCase(
            applicationGateway,
            linkedApplicationGateway,
            parser,
            new CreateApplicationDomainMapper(),
            saveDomainEventService);
  }

  @Test
  void givenValidCommand_whenExecuted_thenReturnsSavedDomain() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);
    UUID generatedId = UUID.randomUUID();
    Instant generatedCreatedAt = Instant.now();
    ArgumentCaptor<ApplicationDomain> domainCaptor =
        stubSaveEnriching(generatedId, generatedCreatedAt);

    ApplicationDomain result = useCase.execute(command);

    assertCommandMappedToDomain(command, domainCaptor.getValue());
    assertThat(result.id()).isEqualTo(generatedId);
    assertThat(result.createdAt()).isEqualTo(generatedCreatedAt);
    verify(saveDomainEventService).saveCreateApplicationDomainEvent(eq(result), any());
    verify(linkedApplicationGateway, never()).link(any(), any());
  }

  @Test
  void givenDuplicateApplyApplicationId_whenExecuted_thenThrowsValidationException() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(true);

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains("Application already exists for Apply Application Id:")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenNoLeadProceeding_whenExecuted_thenThrowsValidationException() {
    ApplicationContent noLeadProceedingContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingGenerator.class, pb -> pb.leadProceeding(false)))));

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(noLeadProceedingContent)));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err -> err.contains("No lead proceeding found in application content")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenLinkedApplicationWithExistingLead_whenExecuted_thenLinksApplications() {
    UUID leadApplyId = UUID.randomUUID();
    UUID associatedApplyId = UUID.randomUUID();
    UUID savedDomainId = UUID.randomUUID();
    UUID leadDomainId = UUID.randomUUID();

    CreateApplicationCommand command = commandWithLinkedApplication(leadApplyId, associatedApplyId);
    ArgumentCaptor<ApplicationDomain> domainCaptor = stubSaveEnriching(savedDomainId, null);
    ApplicationDomain leadDomain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.id(leadDomainId));

    when(applicationGateway.findLeadByApplyApplicationId(leadApplyId))
        .thenReturn(Optional.of(leadDomain));

    useCase.execute(command);

    assertCommandMappedToDomain(command, domainCaptor.getValue());
    verify(linkedApplicationGateway).link(leadDomainId, savedDomainId);
  }

  @Test
  void givenLinkedApplicationWithMissingLead_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID associatedApplyId = UUID.randomUUID();

    CreateApplicationCommand command = commandWithLinkedApplication(leadApplyId, associatedApplyId);
    stubSaveEnriching(UUID.randomUUID(), null);
    when(applicationGateway.findLeadByApplyApplicationId(leadApplyId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("Linking failed > Lead application not found, ID: " + leadApplyId);

    verify(linkedApplicationGateway, never()).link(any(), any());
  }

  @Test
  void
      givenLinkedApplicationWithMissingAssociated_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID associatedApplyId = UUID.randomUUID();

    CreateApplicationCommand command = commandWithLinkedApplication(leadApplyId, associatedApplyId);
    when(applicationGateway.findMissingApplyApplicationIds(any()))
        .thenReturn(List.of(associatedApplyId));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("No linked application found with associated apply ids:");
  }

  @Test
  void
      givenProceedingWithNullUsedDelegatedFunctions_whenExecuted_thenSavedDomainHasNullUsedDelegatedFunctions() {
    ApplicationContent appContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingGenerator.class,
                            pb -> pb.leadProceeding(true).usedDelegatedFunctions(null)))));

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(appContent)));
    ArgumentCaptor<ApplicationDomain> domainCaptor = stubSaveEnriching(UUID.randomUUID(), null);

    useCase.execute(command);

    assertThat(domainCaptor.getValue().usedDelegatedFunctions()).isNull();
  }

  @Test
  void
      givenProceedingWithUsedDelegatedFunctionsTrue_whenExecuted_thenSavedDomainHasTrueUsedDelegatedFunctions() {
    ApplicationContent appContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingGenerator.class,
                            pb -> pb.leadProceeding(true).usedDelegatedFunctions(true)))));

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(appContent)));
    ArgumentCaptor<ApplicationDomain> domainCaptor = stubSaveEnriching(UUID.randomUUID(), null);

    useCase.execute(command);

    assertThat(domainCaptor.getValue().usedDelegatedFunctions()).isTrue();
  }

  @Test
  void givenApplicationContentMissingRequiredFields_whenExecuted_thenThrowsValidationException() {
    // ApplicationContent with no id and no submittedAt — passes deserialization but fails
    // bean validation, matching the integration test case:
    // Map.of("applicationContent", Map.of("proceedings", List.of()))
    ApplicationContent contentMissingRequired =
        ApplicationContent.builder()
            .proceedings(List.of(DataGenerator.createDefault(ProceedingGenerator.class)))
            .build(); // id=null, submittedAt=null

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(contentMissingRequired)));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e -> {
              assertThat(e.errors()).anyMatch(err -> err.contains("id:"));
              assertThat(e.errors()).anyMatch(err -> err.contains("submittedAt:"));
            });

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenNullProceedings_whenExecuted_thenSavesDomainWithEmptyProceedings() {
    ApplicationContent appContent =
        DataGenerator.createDefault(ApplicationContentGenerator.class, b -> b.proceedings(null));

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(appContent)));
    ArgumentCaptor<ApplicationDomain> domainCaptor = stubSaveEnriching(UUID.randomUUID(), null);

    useCase.execute(command);

    assertThat(domainCaptor.getValue().proceedings()).isEmpty();
    verify(applicationGateway).save(any());
  }

  @Test
  void givenMultipleProceedings_whenExecuted_thenSavesDomainWithAllProceedings() {
    ProceedingGenerator proceedingGenerator = new ProceedingGenerator();
    ApplicationContent appContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        proceedingGenerator.createDefault(), proceedingGenerator.createDefault())));

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class,
            b -> b.applicationContent(toContentMap(appContent)));
    ArgumentCaptor<ApplicationDomain> domainCaptor = stubSaveEnriching(UUID.randomUUID(), null);

    useCase.execute(command);

    assertThat(domainCaptor.getValue().proceedings()).hasSize(2);
  }

  // --- helpers ---

  /**
   * Asserts that the domain record captured before {@code save()} was correctly built from the
   * command: pre-save fields ({@code id}, {@code createdAt}) are null, and all fields carried
   * directly from the command are equal.
   */
  private void assertCommandMappedToDomain(
      CreateApplicationCommand command, ApplicationDomain captured) {
    assertThat(captured.id()).isNull();
    assertThat(captured.createdAt()).isNull();
    assertThat(captured.status()).isEqualTo(command.status());
    assertThat(captured.laaReference()).isEqualTo(command.laaReference());
    assertThat(captured.applicationContent()).isEqualTo(command.applicationContent());
    assertThat(captured.individuals()).hasSize(command.individuals().size());
  }

  /**
   * Stubs {@code applicationGateway.save()} to capture the domain passed in and return it enriched
   * with the supplied {@code id} and {@code createdAt} (simulating DB-generated fields). Pass
   * {@code null} for {@code createdAt} when the test does not need to assert on it.
   */
  private ArgumentCaptor<ApplicationDomain> stubSaveEnriching(UUID id, Instant createdAt) {
    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    when(applicationGateway.save(captor.capture()))
        .thenAnswer(
            inv ->
                ((ApplicationDomain) inv.getArgument(0))
                    .toBuilder().id(id).createdAt(createdAt).build());
    return captor;
  }

  /**
   * Builds a {@link CreateApplicationCommand} whose application content declares a linked
   * application relationship between {@code leadApplyId} and {@code associatedApplyId}.
   */
  private CreateApplicationCommand commandWithLinkedApplication(
      UUID leadApplyId, UUID associatedApplyId) {
    ApplicationContent appContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            b ->
                b.id(associatedApplyId)
                    .allLinkedApplications(
                        List.of(
                            LinkedApplication.builder()
                                .leadApplicationId(leadApplyId)
                                .associatedApplicationId(associatedApplyId)
                                .build())));
    return DataGenerator.createDefault(
        CreateApplicationCommandGenerator.class,
        b -> b.applicationContent(toContentMap(appContent)));
  }

  /**
   * Converts an {@link ApplicationContent} to the {@code Map<String, Object>} form used by
   * commands.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> toContentMap(ApplicationContent content) {
    return MapperUtil.getObjectMapper().convertValue(content, Map.class);
  }
}

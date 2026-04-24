package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.usecase.shared.ApplicationConstants.APPLICATION_SCHEMA_VERSION;

import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.CreateApplicationCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.DomainLinkedApplicationsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.DomainProceedingGenerator;
import uk.gov.justice.laa.dstew.access.validation.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreateApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
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
            applicationGateway, domainEventGateway, contentParser, objectMapper);
  }

  @Test
  void givenNewApplication_whenCreateApplication_thenReturnNewIdAndDomainFieldsMappedFromCommand() {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    UUID expectedId = saved.id();
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

    UUID result = useCase.execute(command);

    assertThat(result).isEqualTo(expectedId);
    verify(domainEventGateway).saveCreatedEvent(saved, command.serialisedRequest());

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain domain = captor.getValue();

    // Fields mapped directly from the command
    assertThat(domain.status()).isEqualTo(command.status());
    assertThat(domain.laaReference()).isEqualTo(command.laaReference());
    assertThat(domain.individuals()).isEqualTo(command.individuals());
    assertThat(domain.decision()).isNull();
    assertThat(domain.schemaVersion()).isEqualTo(APPLICATION_SCHEMA_VERSION);

    // Fields parsed from the application content (via ApplicationContentParserService)
    assertThat(domain.officeCode()).isEqualTo("officeCode");
    assertThat(domain.submittedAt()).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
    assertThat(domain.usedDelegatedFunctions()).isTrue();

    // applyApplicationId is the 'id' field from the application content
    UUID contentId = UUID.fromString(command.applicationContent().get("id").toString());
    assertThat(domain.applyApplicationId()).isEqualTo(contentId);
  }

  @Test
  void givenNewApplication_whenCreateApplication_thenProceedingFieldsMappedFromContent() {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ProceedingDomain proceeding = captor.getValue().proceedings().getFirst();

    assertThat(proceeding).isNotNull();

    // Fields built from the content proceeding map (see CreateApplicationUseCase#buildProceedingDomains)
    assertThat(proceeding.isLead()).isTrue();
    assertThat(proceeding.description()).isEqualTo("Proceeding description");
    assertThat(proceeding.meritsDecision()).isNull();

    // applyProceedingId maps from the 'id' field of the content proceeding
    @SuppressWarnings("unchecked")
    var contentProceedings =
        (java.util.List<java.util.Map<String, Object>>) command.applicationContent().get("proceedings");
    UUID expectedProceedingId = UUID.fromString(contentProceedings.getFirst().get("id").toString());
    assertThat(proceeding.applyProceedingId()).isEqualTo(expectedProceedingId);
  }

  @Test
  void givenNewApplicationWithLinkedApplication_whenCreateApplication_thenCallsAddLinkedApplication() {
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
  void givenDuplicateApplyApplicationId_whenCreateApplication_thenThrowValidationException() {
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
  void givenNewApplicationWithMissingAssociatedApplication_whenCreateApplication_thenThrowResourceNotFoundException() {
    UUID leadApplyId = UUID.randomUUID();
    UUID assocId = UUID.randomUUID();
    UUID missingId = UUID.randomUUID();

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

    when(applicationGateway.existsByApplyApplicationId(assocId)).thenReturn(false);
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
  void givenNewApplicationWithMissingLeadApplication_whenCreateApplication_thenThrowResourceNotFoundException() {
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
        .hasMessageContaining("Lead application not found")
        .hasMessageContaining(leadApplyId.toString());
  }

  @Test
  void givenApplicationContentWithNoLeadProceeding_whenCreateApplication_thenThrowValidationException() {
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

    verify(applicationGateway, never()).save(any());
  }

  // ── usedDelegatedFunctions mapping ────────────────────────────────────────

  static Stream<Arguments> usedDelegatedFunctionsCases() {
    DomainProceedingGenerator gen = new DomainProceedingGenerator();
    return Stream.of(
        // lead only, udf=true  → true
        Arguments.of(List.of(gen.createDefault(p -> p.leadProceeding(true).usedDelegatedFunctions(true))), true),
        // lead only, udf=false → false
        Arguments.of(List.of(gen.createDefault(p -> p.leadProceeding(true).usedDelegatedFunctions(false))), false),
        // lead udf=false + non-lead udf=true → true (anyOf logic)
        Arguments.of(
            List.of(
                gen.createDefault(p -> p.leadProceeding(true).usedDelegatedFunctions(false)),
                gen.createDefault(p -> p.leadProceeding(false).usedDelegatedFunctions(true))),
            true),
        // lead udf=false + non-lead udf=false → false
        Arguments.of(
            List.of(
                gen.createDefault(p -> p.leadProceeding(true).usedDelegatedFunctions(false)),
                gen.createDefault(p -> p.leadProceeding(false).usedDelegatedFunctions(false))),
            false));
  }

  @ParameterizedTest
  @MethodSource("usedDelegatedFunctionsCases")
  void givenProceedings_whenCreateApplication_thenUsedDelegatedFunctionsDerivedFromAnyProceeding(
      List<uk.gov.justice.laa.dstew.access.domain.Proceeding> proceedings,
      boolean expectedUsedDelegatedFunctions) {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    var content =
        objectMapper.convertValue(
            DataGenerator.createDefault(
                ApplicationContentDomainGenerator.class, ac -> ac.proceedings(proceedings)),
            Map.class);

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class, b -> b.applicationContent(content));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    assertThat(captor.getValue().usedDelegatedFunctions()).isEqualTo(expectedUsedDelegatedFunctions);
  }

  @Test
  void givenMultipleProceedings_whenCreateApplication_thenAllProceedingDomainsBuiltWithCorrectLeadFlag() {
    ApplicationDomain saved = DataGenerator.createDefault(ApplicationDomainGenerator.class);
    when(applicationGateway.existsByApplyApplicationId(any())).thenReturn(false);
    when(applicationGateway.save(any())).thenReturn(saved);

    DomainProceedingGenerator gen = new DomainProceedingGenerator();
    var leadProceeding = gen.createDefault(p -> p.leadProceeding(true).description("lead proc"));
    var nonLeadProceeding = gen.createDefault(p -> p.leadProceeding(false).description("non-lead proc"));

    var content =
        objectMapper.convertValue(
            DataGenerator.createDefault(
                ApplicationContentDomainGenerator.class,
                ac -> ac.proceedings(List.of(leadProceeding, nonLeadProceeding))),
            Map.class);

    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class, b -> b.applicationContent(content));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    List<ProceedingDomain> builtProceedings = captor.getValue().proceedings();

    assertThat(builtProceedings).hasSize(2);
    assertThat(builtProceedings).anySatisfy(p -> {
      assertThat(p.isLead()).isTrue();
      assertThat(p.description()).isEqualTo("lead proc");
    });
    assertThat(builtProceedings).anySatisfy(p -> {
      assertThat(p.isLead()).isFalse();
      assertThat(p.description()).isEqualTo("non-lead proc");
    });
  }

  @Test
  void givenExecuteMethod_whenInspected_thenCarriesEnforceRoleAnnotationForCaseworker() throws NoSuchMethodException {
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

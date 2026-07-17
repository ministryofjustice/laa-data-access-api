package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class ApplicationCreationDetailsFactoryTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-15T08:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private Repository<ApplicationAggregate> applicationRepository;
  private ApplicationContentParser applicationContentParser;
  private ApplicationCreationDetailsFactory factory;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    applicationRepository = mock(Repository.class);
    applicationContentParser = mock(ApplicationContentParser.class);
    factory =
        new ApplicationCreationDetailsFactory(
            applicationRepository, applicationContentParser, FIXED_CLOCK);
  }

  @Test
  void givenCommand_whenPrepared_thenMapsAllParsedFields() {
    UUID applicationId = UUID.randomUUID();
    UUID applyApplicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetails(applyApplicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(details.laaReference()).isEqualTo("LAA-123");
    assertThat(details.schemaVersion()).isEqualTo(1);
    assertThat(details.applicationType()).isEqualTo("APPLY");
    assertThat(details.applyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(details.occurredAt()).isEqualTo(FIXED_NOW);
    assertThat(details.leadApplicationId()).isNull();
  }

  @Test
  void givenIndividuals_whenPrepared_thenGeneratesIndividualIds() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = commandWithIndividuals(applicationId);
    when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetails(UUID.randomUUID()));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.individuals()).hasSize(1);
    assertThat(details.individuals().getFirst().individualId()).isNotNull();
    assertThat(details.individuals().getFirst().firstName()).isEqualTo("Ada");
  }

  @Test
  void givenNoLinkedApplications_whenPrepared_thenReturnsNullLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetailsWithNoLinks(applicationId));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenExistingLeadApplicationStream_whenPrepared_thenResolvesLeadApplicationId()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetailsWithLead(applicationId, leadApplicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    Aggregate<ApplicationAggregate> mockAggregate = mock(Aggregate.class);
    when(applicationRepository.load(leadApplicationId.toString())).thenReturn(mockAggregate);
    when(mockAggregate.invoke(org.mockito.ArgumentMatchers.any())).thenReturn(true);

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isEqualTo(leadApplicationId);
  }

  @Test
  void givenSelfReferentialLeadApplication_whenPrepared_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetailsWithLead(applicationId, applicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);
    when(applicationRepository.load(applicationId.toString()))
        .thenThrow(new AggregateNotFoundException(applicationId.toString(), "not found"));

    assertThatThrownBy(() -> factory.prepare(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenSelfReferentialAssociatedApplication_whenPrepared_thenSkipsAssociatedStreamValidation()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    // Associated list contains the current applicationId — no stream exists yet for it.
    ParsedAppContentDetails parsed =
        parsedDetailsWithMultipleLinked(applicationId, leadId, applicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    Aggregate<ApplicationAggregate> mockLead = mock(Aggregate.class);
    when(applicationRepository.load(leadId.toString())).thenReturn(mockLead);
    when(mockLead.invoke(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    // No stub for applicationId.toString() — it must not be looked up.

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isEqualTo(leadId);
  }

  @Test
  void givenMissingLeadApplicationStream_whenPrepared_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID missingLeadId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetailsWithLead(applicationId, missingLeadId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);
    when(applicationRepository.load(missingLeadId.toString()))
        .thenThrow(new AggregateNotFoundException(missingLeadId.toString(), "not found"));

    assertThatThrownBy(() -> factory.prepare(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(missingLeadId.toString());
  }

  @Test
  void givenProceedings_whenPrepared_thenGeneratesProceedingIds() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetailsWithProceedings(applicationId, applyProceedingId));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.proceedings()).hasSize(1);
    uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding proc =
        details.proceedings().getFirst();
    assertThat(proc.proceedingId()).isNotNull();
    assertThat(proc.applyProceedingId()).isEqualTo(applyProceedingId);
    assertThat(proc.description()).isEqualTo("Care order");
    assertThat(proc.lead()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenMissingAssociatedApplicationStream_whenPrepared_thenThrowsResourceNotFoundException()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID missingAssociatedId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed =
        parsedDetailsWithMultipleLinked(applicationId, leadId, missingAssociatedId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    // The lead stream is present.
    Aggregate<ApplicationAggregate> mockLead = mock(Aggregate.class);
    when(applicationRepository.load(leadId.toString())).thenReturn(mockLead);
    when(mockLead.invoke(org.mockito.ArgumentMatchers.any())).thenReturn(true);

    // The other associated stream is absent.
    when(applicationRepository.load(missingAssociatedId.toString()))
        .thenThrow(new AggregateNotFoundException(missingAssociatedId.toString(), "not found"));

    assertThatThrownBy(() -> factory.prepare(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(missingAssociatedId.toString());
  }

  private ParsedAppContentDetails parsedDetailsWithProceedings(
      UUID applyApplicationId, UUID proceedingId) {
    uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding proceeding =
        uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding.builder()
            .id(proceedingId)
            .leadProceeding(true)
            .description("Care order")
            .build();
    return new ParsedAppContentDetails(
        null, applyApplicationId, null, null, null, null, null, List.of(proceeding), null);
  }

  private ParsedAppContentDetails parsedDetailsWithMultipleLinked(
      UUID applyApplicationId, UUID leadApplicationId, UUID anotherAssociatedId) {
    List<uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication> linkedApps =
        List.of(
            uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication.builder()
                .leadApplicationId(leadApplicationId)
                .associatedApplicationId(applyApplicationId)
                .build(),
            uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication.builder()
                .leadApplicationId(leadApplicationId)
                .associatedApplicationId(anotherAssociatedId)
                .build());
    return new ParsedAppContentDetails(
        null, applyApplicationId, null, null, null, null, null, List.of(), linkedApps);
  }

  private CreateApplicationCommand command(UUID applicationId) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", applicationId.toString()),
        List.of(),
        "{}",
        1,
        "ApplyApplication.json",
        "APPLY");
  }

  private CreateApplicationCommand commandWithIndividuals(UUID applicationId) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", applicationId.toString()),
        List.of(
            new CreateApplicationIndividual(
                "Ada", "Lovelace", java.time.LocalDate.of(1815, 12, 10), Map.of(), "CLIENT")),
        "{}",
        1,
        "ApplyApplication.json",
        "APPLY");
  }

  private ParsedAppContentDetails parsedDetails(UUID applyApplicationId) {
    return new ParsedAppContentDetails(
        null,
        applyApplicationId,
        CategoryOfLaw.FAMILY,
        MatterType.SPECIAL_CHILDREN_ACT,
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        List.of(),
        null);
  }

  private ParsedAppContentDetails parsedDetailsWithNoLinks(UUID applyApplicationId) {
    return new ParsedAppContentDetails(
        null, applyApplicationId, null, null, null, null, null, List.of(), null);
  }

  private ParsedAppContentDetails parsedDetailsWithLead(
      UUID applyApplicationId, UUID leadApplicationId) {
    uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication linkedApp =
        uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication.builder()
            .leadApplicationId(leadApplicationId)
            .associatedApplicationId(applyApplicationId)
            .build();
    return new ParsedAppContentDetails(
        null, applyApplicationId, null, null, null, null, null, List.of(), List.of(linkedApp));
  }
}

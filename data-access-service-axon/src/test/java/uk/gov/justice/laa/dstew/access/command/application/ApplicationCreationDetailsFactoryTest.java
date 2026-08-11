package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;

class ApplicationCreationDetailsFactoryTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-15T08:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private ApplicationContentParser applicationContentParser;
  private ApplicationCreationDetailsFactory factory;

  @BeforeEach
  void setUp() {
    applicationContentParser = Mockito.mock(ApplicationContentParser.class);
    factory = new ApplicationCreationDetailsFactory(applicationContentParser, FIXED_CLOCK);
  }

  @Test
  void givenCommand_whenPrepared_thenMapsAllParsedFields() {
    UUID applicationId = UUID.randomUUID();
    UUID applyApplicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetails(applyApplicationId);
    Mockito.when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(details.laaReference()).isEqualTo("LAA-123");
    assertThat(details.schemaVersion()).isEqualTo(1);
    assertThat(details.applyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(details.occurredAt()).isEqualTo(FIXED_NOW);
    assertThat(details.leadApplicationId()).isNull();
  }

  @Test
  void givenCommand_whenPrepared_thenOpponentsArePassedThrough() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    Mockito.when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetails(UUID.randomUUID()));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.opponents()).isEmpty();
  }

  @Test
  void givenNoLinkedApplications_whenPrepared_thenReturnsNullLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    Mockito.when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetailsWithNoLinks(applicationId));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isNull();
  }

  @Test
  void givenLinkedApplications_whenPrepared_thenExtractsLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetailsWithLead(applicationId, leadApplicationId);
    Mockito.when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    // No repository lookup — factory simply extracts from parsed content.
    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isEqualTo(leadApplicationId);
  }

  @Test
  void givenProceedings_whenPrepared_thenGeneratesProceedingIds() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    Mockito.when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetailsWithProceedings(applicationId, applyProceedingId));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.proceedings()).hasSize(1);
    uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding proc =
        details.proceedings().getFirst();
    assertThat(proc.getId()).isEqualTo(applyProceedingId);
    assertThat(proc.getDescription()).isEqualTo("Care order");
    assertThat(proc.getLeadProceeding()).isTrue();
  }

  @Test
  void givenMultipleLinkedApplications_whenPrepared_thenExtractsLeadFromFirstEntry() {
    UUID applicationId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID anotherAssociatedId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed =
        parsedDetailsWithMultipleLinked(applicationId, leadId, anotherAssociatedId);
    Mockito.when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.leadApplicationId()).isEqualTo(leadId);
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
        null, null, null, applyApplicationId, null, null, null, null, List.of(proceeding), null);
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
        null, null, null, applyApplicationId, null, null, null, null, List.of(), linkedApps);
  }

  private CreateApplicationCommand command(UUID applicationId) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        validApplicationContent(applicationId, proceedingIdFor(applicationId)),
        "{}",
        1,
        "ApplyApplication.json");
  }

  private ParsedAppContentDetails parsedDetails(UUID applyApplicationId) {
    return new ParsedAppContentDetails(
        null,
        null,
        null,
        applyApplicationId,
        "Family",
        "SPECIAL_CHILDREN_ACT",
        Instant.parse("2026-07-14T12:30:00Z"),
        false,
        List.of(),
        null);
  }

  private ParsedAppContentDetails parsedDetailsWithNoLinks(UUID applyApplicationId) {
    return new ParsedAppContentDetails(
        null, null, null, applyApplicationId, null, null, null, null, List.of(), null);
  }

  private ParsedAppContentDetails parsedDetailsWithLead(
      UUID applyApplicationId, UUID leadApplicationId) {
    uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication linkedApp =
        uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication.builder()
            .leadApplicationId(leadApplicationId)
            .associatedApplicationId(applyApplicationId)
            .build();
    return new ParsedAppContentDetails(
        null,
        null,
        null,
        applyApplicationId,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(linkedApp));
  }

  private UUID proceedingIdFor(UUID applicationId) {
    return UUID.nameUUIDFromBytes(("proceeding-" + applicationId).getBytes());
  }
}

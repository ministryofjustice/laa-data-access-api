package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

class ApplicationCreationDetailsFactoryTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-15T08:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private ApplicationContentParser applicationContentParser;
  private ApplicationCreationDetailsFactory factory;

  @BeforeEach
  void setUp() {
    applicationContentParser = mock(ApplicationContentParser.class);
    factory = new ApplicationCreationDetailsFactory(applicationContentParser, FIXED_CLOCK);
  }

  @Test
  void givenCommand_whenPrepared_thenMapsAllParsedFields() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    ParsedAppContentDetails parsed = parsedDetails();
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsed);

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(details.laaReference()).isEqualTo("LAA-123");
    assertThat(details.schemaVersion()).isEqualTo(1);
    assertThat(details.occurredAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void givenCommand_whenPrepared_thenOpponentsArePassedThrough() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsedDetails());

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.opponents()).isEmpty();
  }

  @Test
  void givenProceedings_whenPrepared_thenGeneratesProceedingIds() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    when(applicationContentParser.parse(command.applicationContent()))
        .thenReturn(parsedDetailsWithProceedings(applyProceedingId));

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.proceedings()).hasSize(1);
    Proceeding proceeding = details.proceedings().getFirst();
    assertThat(proceeding.getId()).isEqualTo(applyProceedingId);
    assertThat(proceeding.getDescription()).isEqualTo("Care order");
    assertThat(proceeding.getLeadProceeding()).isTrue();
  }

  @Test
  void givenCreationDetailsRecord_whenInspected_thenContainsOnlyCreationFields() {
    List<String> componentNames =
        Arrays.stream(ApplicationCreationDetails.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .containsExactly(
            "status",
            "laaReference",
            "client",
            "provider",
            "opponents",
            "schemaVersion",
            "submittedAt",
            "usedDelegatedFunctions",
            "categoryOfLaw",
            "matterType",
            "proceedings",
            "serialisedRequest",
            "occurredAt",
            "potentialDuplicates");
  }

  @Test
  void givenCommandWithNullPotentialDuplicates_whenPrepared_thenNormalizesToEmptyList() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationCommand command = command(applicationId);
    when(applicationContentParser.parse(command.applicationContent())).thenReturn(parsedDetails());

    ApplicationCreationDetails details = factory.prepare(command);

    assertThat(details.potentialDuplicates()).isEmpty();
  }

  @Test
  void
      givenCommandWithPopulatedPotentialDuplicates_whenPrepared_thenPassesThroughAndMakesImmutable() {
    UUID applicationId = UUID.randomUUID();
    List<PotentialDuplicate> duplicates =
        List.of(new PotentialDuplicate("LAA-456"), new PotentialDuplicate("LAA-789"));
    var mutableDuplicates = new java.util.ArrayList<>(duplicates);
    CreateApplicationCommand commandWithDuplicates =
        new CreateApplicationCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            "LAA-123",
            validApplicationContent(applicationId, proceedingIdFor(applicationId)),
            "{}",
            1,
            "BaseCivilApplication.json",
            mutableDuplicates);
    when(applicationContentParser.parse(commandWithDuplicates.applicationContent()))
        .thenReturn(parsedDetails());

    ApplicationCreationDetails details = factory.prepare(commandWithDuplicates);

    assertThat(details.potentialDuplicates()).isEqualTo(duplicates);
    assertThatThrownBy(() -> details.potentialDuplicates().add(new PotentialDuplicate("BOOM")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private ParsedAppContentDetails parsedDetailsWithProceedings(UUID proceedingId) {
    Proceeding proceeding =
        Proceeding.builder()
            .id(proceedingId)
            .leadProceeding(true)
            .description("Care order")
            .build();
    return new ParsedAppContentDetails(
        null, null, null, null, null, null, null, List.of(proceeding));
  }

  private CreateApplicationCommand command(UUID applicationId) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        validApplicationContent(applicationId, proceedingIdFor(applicationId)),
        "{}",
        1,
        "BaseCivilApplication.json",
        null);
  }

  private ParsedAppContentDetails parsedDetails() {
    return new ParsedAppContentDetails(
        null,
        null,
        null,
        "Family",
        "SPECIAL_CHILDREN_ACT",
        Instant.parse("2026-07-14T12:30:00Z"),
        false,
        List.of());
  }

  private UUID proceedingIdFor(UUID applicationId) {
    return UUID.nameUUIDFromBytes(("proceeding-" + applicationId).getBytes());
  }
}

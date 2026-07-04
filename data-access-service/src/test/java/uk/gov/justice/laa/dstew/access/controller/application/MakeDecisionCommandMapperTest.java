package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMakeDecisionRequestGenerator;

class MakeDecisionCommandMapperTest {

  private MakeDecisionCommandMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new MakeDecisionCommandMapper(new ObjectMapper());
  }

  @Test
  void givenFullyPopulatedRequest_whenToMakeDecisionCommand_thenMapsAllFieldsCorrectly() {
    UUID applicationId = UUID.randomUUID();
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            b ->
                b.overallDecision(DecisionStatus.GRANTED)
                    .autoGranted(false)
                    .eventHistory(EventHistoryRequest.builder().eventDescription("desc").build())
                    .certificate(Map.of("key", "val"))
                    .applicationVersion(2L));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.applicationVersion()).isEqualTo(2L);
    assertThat(command.overallDecision()).isEqualTo("GRANTED");
    assertThat(command.autoGranted()).isFalse();
    assertThat(command.certificate()).isEqualTo(Map.of("key", "val"));
    assertThat(command.eventDescription()).isEqualTo("desc");
    assertThat(command.proceedings()).hasSize(1);
    assertThat(command.serialisedRequest()).contains("overallDecision");
  }

  @Test
  void givenNullCertificate_whenToMakeDecisionCommand_thenCertificateIsNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class, b -> b.certificate(null));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.certificate()).isNull();
  }

  @Test
  void givenNullEventHistory_whenToMakeDecisionCommand_thenEventDescriptionIsNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class, b -> b.eventHistory(null));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.eventDescription()).isNull();
  }

  @Test
  void givenNullEventDescription_whenToMakeDecisionCommand_thenEventDescriptionIsNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            b -> b.eventHistory(EventHistoryRequest.builder().eventDescription(null).build()));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.eventDescription()).isNull();
  }

  @Test
  void givenNullOverallDecision_whenToMakeDecisionCommand_thenOverallDecisionIsNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class, b -> b.overallDecision(null));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.overallDecision()).isNull();
  }

  @Test
  void givenNullProceedings_whenToMakeDecisionCommand_thenProceedingsIsEmpty() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class, b -> b.proceedings(null));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.proceedings()).isEmpty();
  }

  @Test
  void givenProceeding_whenToMakeDecisionCommand_thenProceedingFieldsMapped() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class);

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.proceedings()).hasSize(1);
    var procCmd = command.proceedings().get(0);
    assertThat(procCmd.proceedingId()).isEqualTo(request.getProceedings().get(0).getProceedingId());
    assertThat(procCmd.decision()).isEqualTo("REFUSED");
  }

  @Test
  void givenRefusedRequest_whenToMakeDecisionCommand_thenSerialisedRequestContainsRefused() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            b -> b.overallDecision(DecisionStatus.REFUSED));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.serialisedRequest()).contains("REFUSED");
  }

  @Test
  void givenEmptyProceedingsList_whenToMakeDecisionCommand_thenProceedingsIsEmpty() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class, b -> b.proceedings(List.of()));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.proceedings()).isEmpty();
  }

  @Test
  void givenProceedingWithNullMeritsDecision_whenToMakeDecisionCommand_thenMeritsFieldsAreNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest
                            .builder()
                            .proceedingId(UUID.randomUUID())
                            .meritsDecision(null)
                            .build())));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.proceedings()).hasSize(1);
    var procCmd = command.proceedings().get(0);
    assertThat(procCmd.decision()).isNull();
    assertThat(procCmd.reason()).isNull();
    assertThat(procCmd.justification()).isNull();
  }

  @Test
  void
      givenProceedingWithNullDecisionInMeritsDecision_whenToMakeDecisionCommand_thenDecisionIsNull() {
    MakeDecisionRequest request =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            b ->
                b.proceedings(
                    List.of(
                        uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest
                            .builder()
                            .proceedingId(UUID.randomUUID())
                            .meritsDecision(
                                uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest
                                    .builder()
                                    .decision(null)
                                    .reason("a reason")
                                    .justification("a justification")
                                    .build())
                            .build())));

    MakeDecisionCommand command = mapper.toMakeDecisionCommand(UUID.randomUUID(), request);

    assertThat(command.proceedings()).hasSize(1);
    var procCmd = command.proceedings().get(0);
    assertThat(procCmd.decision()).isNull();
    assertThat(procCmd.reason()).isEqualTo("a reason");
    assertThat(procCmd.justification()).isEqualTo("a justification");
  }
}

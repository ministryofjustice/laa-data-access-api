package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMakeDecisionRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationUpdateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionDetailsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.MakeDecisionProceedingGenerator;

class ApplicationValidationsTest {

  private ApplicationValidations applicationValidations;

  @BeforeEach
  void setUp() {
    EffectiveAuthorizationProvider effectiveAuthorizationProvider =
        mock(EffectiveAuthorizationProvider.class);
    applicationValidations = new ApplicationValidations(effectiveAuthorizationProvider);
  }

  @Test
  void givenNullUpdateRequest_whenValidated_thenThrowsValidationException() {
    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> applicationValidations.checkApplicationUpdateRequest(null))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "ApplicationUpdateRequest and its content cannot be null")));
  }

  @Test
  void givenNullApplicationContent_whenValidated_thenThrowsValidationException() {
    var applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class, builder -> builder.applicationContent(null));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> applicationValidations.checkApplicationUpdateRequest(applicationUpdateRequest))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Application content cannot be null")));
  }

  @Test
  void givenEmptyApplicationContent_whenValidated_thenThrowsValidationException() {
    var applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class,
            builder -> builder.applicationContent(new HashMap<>()));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> applicationValidations.checkApplicationUpdateRequest(applicationUpdateRequest))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Application content cannot be empty")));
  }

  @Test
  void givenValidUpdateRequest_whenValidated_thenNoException() {
    var applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    assertThatNoException()
        .isThrownBy(
            () -> applicationValidations.checkApplicationUpdateRequest(applicationUpdateRequest));
  }

  @Test
  void givenIdListContainingNull_whenValidated_thenThrowsValidationException() {
    List<UUID> applicationIds = new java.util.ArrayList<>();
    applicationIds.add(UUID.randomUUID());
    applicationIds.add(null);

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> applicationValidations.checkApplicationIdList(applicationIds))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Request contains null values for ids")));
  }

  @Test
  void givenIdListWithoutNulls_whenValidated_thenNoException() {
    assertThatNoException()
        .isThrownBy(
            () ->
                applicationValidations.checkApplicationIdList(
                    List.of(UUID.randomUUID(), UUID.randomUUID())));
  }

  @Test
  void givenNoProceedings_whenMakeDecisionValidated_thenThrowsValidationException() {
    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> builder.proceedings(List.of()));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> applicationValidations.checkApplicationMakeDecisionRequest(makeDecisionRequest))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "The Make Decision request must contain at least one proceeding")));
  }

  @Test
  void
      givenGrantedDecisionWithoutCertificate_whenMakeDecisionValidated_thenThrowsValidationException() {
    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.GRANTED)
                    .certificate(null)
                    .proceedings(List.of(validProceedingWithJustification())));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> applicationValidations.checkApplicationMakeDecisionRequest(makeDecisionRequest))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "The Make Decision request must contain a certificate when overallDecision is GRANTED")));
  }

  @Test
  void
      givenProceedingMissingJustification_whenMakeDecisionValidated_thenThrowsValidationException() {
    UUID proceedingId = UUID.randomUUID();
    MakeDecisionProceedingRequest makeDecisionProceedingRequest =
        DataGenerator.createDefault(
            MakeDecisionProceedingGenerator.class,
            builder ->
                builder
                    .proceedingId(proceedingId)
                    .meritsDecision(
                        DataGenerator.createDefault(
                            MeritsDecisionDetailsGenerator.class,
                            meritsBuilder -> meritsBuilder.justification(null))));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(makeDecisionProceedingRequest)));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> applicationValidations.checkApplicationMakeDecisionRequest(makeDecisionRequest))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "The Make Decision request must contain a refusal justification for proceeding with id: "
                                    + proceedingId)));
  }

  @Test
  void givenValidMakeDecisionRequest_whenValidated_thenNoException() {
    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.GRANTED)
                    .certificate(new HashMap<>(Map.of("certificate", "value")))
                    .proceedings(List.of(validProceedingWithJustification())));

    assertThatNoException()
        .isThrownBy(
            () -> applicationValidations.checkApplicationMakeDecisionRequest(makeDecisionRequest));
  }

  private MakeDecisionProceedingRequest validProceedingWithJustification() {
    MeritsDecisionDetailsRequest meritsDecisionDetailsRequest =
        DataGenerator.createDefault(
            MeritsDecisionDetailsGenerator.class,
            builder -> builder.justification("refusal reason"));

    return DataGenerator.createDefault(
        MakeDecisionProceedingGenerator.class,
        builder -> builder.meritsDecision(meritsDecisionDetailsRequest));
  }
}

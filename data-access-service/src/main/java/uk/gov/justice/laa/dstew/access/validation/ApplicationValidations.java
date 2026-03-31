package uk.gov.justice.laa.dstew.access.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Class that runs validations for Access.
 */
@Component
@RequiredArgsConstructor
public class ApplicationValidations {

  private final EffectiveAuthorizationProvider entra;


  /**
   * One by
   * Validates an incoming PATCH.
   */
  public void checkApplicationUpdateRequest(final ApplicationUpdateRequest dto) {
    ValidationErrors validationErrors = ValidationErrors
        .empty()
        .addIf((dto == null), "ApplicationUpdateRequest and its content cannot be null")
        .addIf(dto.getApplicationContent() == null, "Application content cannot be null")
        .addIf(dto.getApplicationContent().isEmpty(), "Application content cannot be empty");
    if (!validationErrors.errors().isEmpty()) {
      throw new ValidationException(
          validationErrors
              .errors()
              .stream()
              .distinct()
              .toList());
    }
  }

  /**
   * Validates a list of application ids and throw ValidationException.
   */
  public void checkApplicationIdList(final List<UUID> appIds) {
    if (appIds.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          List.of("Request contains null values for ids")
      );
    }
  }

  /**
   * Validates an incoming apply Decision PATCH.
   */
  public void checkApplicationMakeDecisionRequest(final MakeDecisionRequest dto) {
    if (dto == null || dto.getProceedings().isEmpty()) {
      throw new ValidationException(
              List.of("The Make Decision request must contain at least one proceeding")
      );
    }

    // Validate certificate is provided when overallDecision is GRANTED
    if (dto.getOverallDecision() == DecisionStatus.GRANTED && isCertificateNullOrEmpty(dto.getCertificate())) {
      throw new ValidationException(
              List.of("The Make Decision request must contain a certificate when overallDecision is GRANTED")
      );
    }

    dto.getProceedings().forEach(proceeding -> {
      MeritsDecisionDetailsRequest mdd = proceeding.getMeritsDecision();
      if (mdd.getJustification() == null || mdd.getJustification().isEmpty()) {
        throw new ValidationException(
                List.of("The Make Decision request must contain a refusal justification for proceeding with id: "
                        + proceeding.getProceedingId()));
      }
    });
  }

  private boolean isCertificateNullOrEmpty(Object certificate) {
    if (certificate == null) {
      return true;
    }
    if (certificate instanceof Map) {
      return ((Map<?, ?>) certificate).isEmpty();
    }
    return certificate.toString().isEmpty();
  }
}
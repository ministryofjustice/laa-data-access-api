package uk.gov.justice.laa.dstew.access.validation;

import static uk.gov.justice.laa.dstew.access.validation.ValidationUtils.notNull;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Validations for a particular domain (Applications).
 * Currently, this avoids any kind of validation framework apart from a holder for validation errors (ValidationState)
 * and an exception that wraps those errors (ValidationException).
 */
@Component
@RequiredArgsConstructor
public class ApplicationValidations {
  private final EffectiveAuthorizationProvider entra;

  /**
   * Validate an ApplicationCreateRequest instance.
   *
   * @param dto DTO to validate.
   */
  public void checkApplicationCreateRequest(final ApplicationCreateRequest dto) {
    final var state = ValidationErrors.empty();
    state.addIf(
              (dto.getProviderOfficeId() == null)
                    && (!(notNull(dto.getIsEmergencyApplication()) && (dto.getStatusCode().contentEquals("NEW")))
              ),
            "BRR-01: Provider office id is required (unless unsubmitted ECT)");
    state.throwIfAny();
  }

  /**
   * Validate an ApplicationUpdateRequest instance.
   *
   * @param dto     DTO to validate.
   * @param current existing persisted entity.
   */
  public void checkApplicationUpdateRequest(final ApplicationUpdateRequest dto,
                                          final ApplicationEntity current) {
    ValidationErrors.empty()
            .addIf(entra.hasAppRole("Provider")
                            && !entra.hasAnyAppRole("Caseworker", "Administrator")
                            && (dto.getClientId() != null),
                    "BRR-03: Provider role cannot update the client date of birth or NI number")
            .throwIfAny();
  }
}
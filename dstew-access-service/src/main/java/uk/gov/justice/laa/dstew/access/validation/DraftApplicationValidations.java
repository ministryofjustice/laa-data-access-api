package uk.gov.justice.laa.dstew.access.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Validations for a draft application.
 * Validates that at least one of the 3 initial fields are present
 */
@Component
@RequiredArgsConstructor
public class DraftApplicationValidations {
  private final EffectiveAuthorizationProvider entra;

  /**
   * Validate an ApplicationCreateRequest instance.
   *
   * @param dto DTO to validate.
   * */
  public void checkCreateRequest(final DraftApplicationCreateRequest dto) {
    final var state = ValidationErrors.empty();
    state.throwIfAny();
  }

  /**
   * Validate an DraftApplication instance.
   *
   * @param dto     DTO to validate.
   * @param current existing persisted entity.
   */
  public void checkDraftApplicationUpdateRequest(final DraftApplicationUpdateRequest dto,
                                                final DraftApplicationEntity current) {
    ValidationErrors.empty()
        .addIf(dto.getClientId() != null
                && entra.hasAppRole("Provider")
                && !entra.hasAnyAppRole("Caseworker", "Administrator"),
            "BRR-03: Provider role cannot update the client date of birth or NI number")
        .throwIfAny();
  }
}

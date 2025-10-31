package uk.gov.justice.laa.dstew.access.validation;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Validations for Application DTOs using JSONB applicationContent.
 * Only validates that applicationContent exists and is a JSON object.
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

    Map<String, Object> content = dto.getApplicationContent();
    if (content == null) {
      state.add("BRR-00: applicationContent must not be null");
    } else if (!(content instanceof Map)) {
      state.add("BRR-01: applicationContent must be a valid JSON object");
    }

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
    final var state = ValidationErrors.empty();

    Map<String, Object> content = dto.getApplicationContent();
    if (content == null) {
      state.add("BRR-00: applicationContent must not be null");
    } else if (!(content instanceof Map)) {
      state.add("BRR-01: applicationContent must be a valid JSON object");
    }

    state.throwIfAny();
  }
}

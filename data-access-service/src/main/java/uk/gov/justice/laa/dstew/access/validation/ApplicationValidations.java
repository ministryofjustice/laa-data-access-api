package uk.gov.justice.laa.dstew.access.validation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Class that runs validations for Access.
 */
@Component
@RequiredArgsConstructor
public class ApplicationValidations {

  private final EffectiveAuthorizationProvider entra;

  /**
   * Validates an incoming POST.
   */
  public void checkApplicationCreateRequest(final ApplicationCreateRequest dto) {
    if (dto == null || dto.getApplicationContent() == null) {
      throw new ValidationException(
          List.of("ApplicationCreateRequest and its content cannot be null")
      );
    }

    if (dto.getApplicationContent().isEmpty()) {
      throw new ValidationException(
          List.of("Application content cannot be empty")
      );
    }
  }

  /**
   * Validates an incoming PATCH.
   */
  public void checkApplicationUpdateRequest(final ApplicationUpdateRequest dto,
                                            final ApplicationEntity current) {
    if (dto == null || dto.getApplicationContent() == null) {
      throw new ValidationException(
          List.of("ApplicationUpdateRequest and its content cannot be null")
      );
    }

    if (dto.getApplicationContent().isEmpty()) {
      throw new ValidationException(
          List.of("Application content cannot be empty")
      );
    }

    if (entra.hasAppRole("Provider")
        && !entra.hasAnyAppRole("Caseworker", "Administrator")
        && !dto.getApplicationContent().isEmpty()) {
      throw new ValidationException(
          List.of("Provider role cannot update the client data")
      );
    }
  }
}

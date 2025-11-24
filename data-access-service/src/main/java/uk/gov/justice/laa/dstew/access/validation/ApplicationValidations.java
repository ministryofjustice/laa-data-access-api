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
  private final IndividualValidations individualValidator;

  /**
   * Validates an incoming POST.
   */
  public void checkApplicationCreateRequest(final ApplicationCreateRequest dto) {
    
    if (dto == null || dto.getApplicationContent() == null) {
      throw new ValidationException(
          List.of("ApplicationCreateRequest and its content cannot be null")
      );
    }

    if (dto.getStatus() == null) {
      throw new ValidationException(
          List.of("Application status cannot be null")
      );
    }

    if (dto.getApplicationContent().isEmpty()) {
      throw new ValidationException(
          List.of("Application content cannot be empty")
      );
    }

    if (dto.getApplicationReference() == null || dto.getApplicationReference().isBlank()) {
      throw new ValidationException(
        List.of("Application reference cannot be blank")
      );
    }

    List<String> individualsValidationErrors = dto.getIndividuals()
                                                  .stream()
                                                  .map(individualValidator::validateIndividual)
                                                  .flatMap(s -> s.stream())
                                                  .distinct()
                                                  .toList();
    if (!individualsValidationErrors.isEmpty()) {
      throw new ValidationException(individualsValidationErrors);
    }
  }

  /**One by
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
  }
}
package uk.gov.justice.laa.dstew.access.validation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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


    List<String> individualsValidationErrors = dto.getIndividuals()
        .stream()
        .map(individualValidator::validateIndividual)
        .flatMap(s -> s.errors().stream())
        .distinct()
        .toList();

    if (!individualsValidationErrors.isEmpty()) {
      throw new ValidationException(individualsValidationErrors);
    }
  }

  /**
   * One by
   * Validates an incoming PATCH.
   */
  public void checkApplicationUpdateRequest(final ApplicationUpdateRequest dto) {
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
}
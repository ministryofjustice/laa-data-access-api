package uk.gov.justice.laa.dstew.access.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    ValidationErrors validationErrors = ValidationErrors.empty()
        .addIf(dto.getApplicationContent().isEmpty(), "Application content cannot be empty")
        .addIf(invalidStatus(dto), "Application status cannot be null")
        .addIf(dto.getIndividuals() == null, "Application individual cannot be null");
    List<String> allErrors = new ArrayList<>(validationErrors.errors());

    if (dto.getIndividuals() != null) {
      List<String> individualsValidationErrors = dto.getIndividuals()
          .stream()
          .map(individualValidator::validateIndividual)
          .flatMap(s -> s.errors().stream())
          .distinct()
          .toList();
      allErrors.addAll(individualsValidationErrors);
    }
    if (!allErrors.isEmpty()) {
      throw new ValidationException(allErrors);
    }
  }



  private static boolean invalidStatus(ApplicationCreateRequest dto) {
    return dto.getStatus() == null;
  }

  /**
   * One by
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

  /**
   * Validates a list of application ids and throw ValidationException.
   */
  public void checkApplicationIdList(final List<UUID> appIds) {
    if (appIds.stream().anyMatch(id -> id == null)) {
      throw new ValidationException(
          List.of("Request contains null values for ids")
      );
    }
  }
}
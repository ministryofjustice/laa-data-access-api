package uk.gov.justice.laa.dstew.access.usecase.shared.validation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Shared validation utilities for use cases. */
public final class UseCaseValidations {

  private UseCaseValidations() {}

  /**
   * Validates that no ID in the list is null.
   *
   * @param appIds list of UUIDs to validate
   * @throws ValidationException if any ID is null
   */
  public static void checkApplicationIdList(List<UUID> appIds) {
    if (appIds.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(List.of("Request contains null values for ids"));
    }
  }
}

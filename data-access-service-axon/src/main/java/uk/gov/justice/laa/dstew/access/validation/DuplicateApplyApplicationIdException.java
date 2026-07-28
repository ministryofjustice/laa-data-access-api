package uk.gov.justice.laa.dstew.access.validation;

import java.util.List;
import java.util.UUID;

/** Validation failure raised when an Apply Application identifier is already claimed. */
public class DuplicateApplyApplicationIdException extends ValidationException {

  public DuplicateApplyApplicationIdException(UUID applyApplicationId) {
    super(List.of("Application already exists for Apply Application Id: " + applyApplicationId));
  }
}

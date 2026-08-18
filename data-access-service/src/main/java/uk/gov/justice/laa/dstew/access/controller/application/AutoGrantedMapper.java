package uk.gov.justice.laa.dstew.access.controller.application;

import uk.gov.justice.laa.dstew.access.model.AutoGranted;

/** Maps the legacy nullable database flag to the explicit API assessment state. */
public final class AutoGrantedMapper {

  private AutoGrantedMapper() {}

  /** Maps the legacy nullable database flag to the explicit API assessment state. */
  public static AutoGranted fromLegacyFlag(Boolean value) {
    if (value == null) {
      return AutoGranted.PENDING;
    }
    return value ? AutoGranted.AUTOGRANTED : AutoGranted.MANUAL;
  }
}

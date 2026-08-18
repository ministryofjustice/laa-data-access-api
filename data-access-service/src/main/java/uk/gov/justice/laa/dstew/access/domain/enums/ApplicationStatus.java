package uk.gov.justice.laa.dstew.access.domain.enums;

/** Domain enum representing the status for a legal aid application. */
public enum ApplicationStatus {
  APPLICATION_IN_PROGRESS("APPLICATION_IN_PROGRESS"),

  APPLICATION_SUBMITTED("APPLICATION_SUBMITTED");
  private final String value;

  ApplicationStatus(String value) {
    this.value = value;
  }
}

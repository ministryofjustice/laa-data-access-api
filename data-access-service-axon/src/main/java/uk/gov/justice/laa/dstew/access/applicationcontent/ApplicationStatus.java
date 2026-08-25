package uk.gov.justice.laa.dstew.access.applicationcontent;

/** Usecase-level enum representing the status for a legal aid application. */
public enum ApplicationStatus {
  APPLICATION_IN_PROGRESS("APPLICATION_IN_PROGRESS"),
  APPLICATION_SUBMITTED("APPLICATION_SUBMITTED"),
  APPLICATION_REFUSED("APPLICATION_REFUSED"),
  APPLICATION_GRANTED("APPLICATION_GRANTED");
  private final String value;

  ApplicationStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}

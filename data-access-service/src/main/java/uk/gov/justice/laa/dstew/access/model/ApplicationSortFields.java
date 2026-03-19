package uk.gov.justice.laa.dstew.access.model;

import lombok.Getter;

/**
 * Represents the fields that applications retrieval can be sorted by.
 */
@Getter
public enum ApplicationSortFields {

  LAST_UPDATED_DATE("modifiedAt"),
  SUBMITTED_DATE("submittedAt");

  private final String value;

  ApplicationSortFields(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}


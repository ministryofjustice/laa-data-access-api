package uk.gov.justice.laa.dstew.access.entity;

import java.time.Instant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Projection interface for application summary queries.
 * Used with Spring Data's fluent query API to select only the required columns.
 */
public interface ApplicationSummaryResult {

  UUID getId();

  ApplicationStatus getStatus();

  String getLaaReference();

  String getOfficeCode();

  Instant getSubmittedAt();

  Instant getModifiedAt();

  Boolean getUsedDelegatedFunctions();

  CategoryOfLaw getCategoryOfLaw();

  MatterType getMatterType();

  Boolean getIsAutoGranted();

  Boolean getIsLead();

  UUID getCaseworkerId();

  String getClientFirstName();

  String getClientLastName();

  LocalDate getClientDateOfBirth();
}


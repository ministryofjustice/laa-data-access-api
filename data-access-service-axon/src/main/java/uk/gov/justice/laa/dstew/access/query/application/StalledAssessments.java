package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;

/** Reconciliation query result containing no Application content or personal data. */
public record StalledAssessments(List<StalledAssessment> applications) {

  public StalledAssessments {
    applications = List.copyOf(applications);
  }
}

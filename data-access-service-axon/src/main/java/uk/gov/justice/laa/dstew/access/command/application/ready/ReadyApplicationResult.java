package uk.gov.justice.laa.dstew.access.command.application.ready;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Outcome of idempotently recording manual-assessment readiness. */
@ExcludeFromGeneratedCodeCoverage
public enum ReadyApplicationResult {
  RECORDED,
  ALREADY_RECORDED
}

package uk.gov.justice.laa.dstew.access.command.application.ready;

/** Outcome of idempotently recording manual-assessment readiness. */
public enum ReadyApplicationResult {
  RECORDED,
  ALREADY_RECORDED
}

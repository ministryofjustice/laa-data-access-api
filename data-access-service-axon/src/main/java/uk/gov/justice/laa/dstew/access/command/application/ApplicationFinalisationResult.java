package uk.gov.justice.laa.dstew.access.command.application;

/** Outcome used by the Saga to distinguish successful creation from command redelivery. */
public enum ApplicationFinalisationResult {
  CREATED,
  ALREADY_CREATED
}

package uk.gov.justice.laa.dstew.access.command.application.data;

/** Sensitive decision details for one proceeding. */
public record ApplicationMeritsDecision(String decision, String reason, String justification) {}

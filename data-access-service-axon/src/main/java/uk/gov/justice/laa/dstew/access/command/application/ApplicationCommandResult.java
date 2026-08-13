package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;

/** The committed aggregate sequence produced by an Application command. */
public record ApplicationCommandResult(UUID applicationId, long aggregateSequence) {}

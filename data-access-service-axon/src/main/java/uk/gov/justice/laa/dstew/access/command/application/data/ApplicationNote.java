package uk.gov.justice.laa.dstew.access.command.application.data;

import java.time.Instant;

/** A single application note stored inside an immutable data version. */
public record ApplicationNote(String noteText, Instant createdAt) {}

package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The assignment an application hands to a linked group when it joins: its caseworker if it was an
 * active manual work item, otherwise null. A record (not a bare UUID) so it can be a map value and
 * a command result.
 */
public record DelegatedAssignment(@Nullable UUID caseworkerId) {}

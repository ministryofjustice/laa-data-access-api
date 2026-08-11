package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/**
 * Diagnostic/recovery query: reconstructs an Application's current-state read model directly from
 * the raw Axon event store, bypassing the Axon query API's projection entirely.
 */
public record FindApplicationByIdRebuildQueryNative(UUID applicationId) {}

package uk.gov.justice.laa.dstew.access.integrationevent;

import java.util.UUID;

/** Identifiers used by a consumer to fetch the authoritative submitted Application. */
public record ApplicationSubmittedData(
    UUID applicationId, String laaReference, long applicationVersion) {}

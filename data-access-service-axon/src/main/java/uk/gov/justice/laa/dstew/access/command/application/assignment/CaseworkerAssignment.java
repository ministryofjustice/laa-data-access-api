package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.util.UUID;

/** Transport data for assigning one caseworker to one Application. */
public record CaseworkerAssignment(
    UUID caseworkerId, UUID applicationId, String serialisedRequest, String eventDescription) {}

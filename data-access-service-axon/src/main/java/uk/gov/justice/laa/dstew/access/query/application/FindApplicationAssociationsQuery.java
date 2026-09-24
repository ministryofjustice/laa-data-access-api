package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Finds the linked group and prior authorities associated with an Application. */
public record FindApplicationAssociationsQuery(UUID applicationId) {}

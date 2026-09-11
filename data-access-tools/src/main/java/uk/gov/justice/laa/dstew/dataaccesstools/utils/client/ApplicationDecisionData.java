package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import java.util.List;
import java.util.UUID;

/** Current application data required to make a decision for all of its proceedings. */
public record ApplicationDecisionData(
    String laaReference, List<UUID> proceedingIds, long applicationVersion) {}

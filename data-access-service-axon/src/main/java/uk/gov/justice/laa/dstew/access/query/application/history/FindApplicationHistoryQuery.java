package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.UUID;

/** Query for history events belonging to an application, optionally restricted by event type. */
public record FindApplicationHistoryQuery(UUID applicationId, List<String> eventTypes) {}

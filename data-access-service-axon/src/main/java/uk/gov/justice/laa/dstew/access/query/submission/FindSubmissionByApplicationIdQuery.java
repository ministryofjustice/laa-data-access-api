package uk.gov.justice.laa.dstew.access.query.submission;

import java.util.UUID;

/** Query for the raw submission payload of a given application. */
public record FindSubmissionByApplicationIdQuery(UUID applyApplicationId) {}

package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/**
 * Checks whether the Application projection has processed its submission, i.e. its creation event.
 */
public record ApplicationSubmissionProjectedQuery(UUID applicationId) {}

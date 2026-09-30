package uk.gov.justice.laa.dstew.access.command.application.data;

import java.util.Map;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Contains the content of an Application that has not yet been submitted.
 *
 * <p>The application content is stored as a raw map and parsed into typed fields when the draft is
 * submitted.
 */
@ExcludeFromGeneratedCodeCoverage
public record ApplicationDraftPayload(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest) {}

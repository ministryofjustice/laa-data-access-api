package uk.gov.justice.laa.dstew.access.command.application.data;

import java.util.Map;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Mutable, unvalidated draft content for an Application that has not yet been submitted.
 *
 * <p>Unlike {@link ApplicationDataPayload}, the {@code applicationContent} here is stored as a raw
 * map rather than parsed into typed fields: schema validation and content parsing are deferred to
 * submit time, mirroring how Prior Authority defers its own draft-content validation.
 */
@ExcludeFromGeneratedCodeCoverage
public record ApplicationDraftPayload(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest) {}

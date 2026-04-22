package uk.gov.justice.laa.dstew.access.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/**
 * Domain record representing the raw application content payload. Used in test-data generators and
 * serialised to Map for use-case commands. Field names intentionally mirror the model class so that
 * Jackson round-trips cleanly through ApplicationContentParserService.
 */
@Builder(toBuilder = true)
public record ApplicationContent(
    UUID id,
    List<Proceeding> proceedings,
    String submittedAt,
    String lastNameAtBirth,
    String previousApplicationId,
    String correspondenceAddressType,
    String submitterEmail,
    String status,
    String laaReference,
    Map<String, Object> applicant,
    Map<String, Object> office,
    Map<String, Object> applicationMerits,
    List<LinkedApplication> allLinkedApplications) {}

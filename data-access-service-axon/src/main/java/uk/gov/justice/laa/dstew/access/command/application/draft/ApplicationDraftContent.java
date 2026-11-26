package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * The content of an open Application draft, read from the aggregate's events.
 *
 * @param documentFilenames original filenames of the draft's documents, by document ID
 */
public record ApplicationDraftContent(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    List<PotentialDuplicate> potentialDuplicates,
    Map<UUID, String> documentFilenames) {}

package uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker;

import java.util.UUID;
import lombok.Builder;

/** Input command for the unassignCaseworker use case. */
@Builder(toBuilder = true)
public record UnassignCaseworkerCommand(UUID applicationId, String eventDescription) {}

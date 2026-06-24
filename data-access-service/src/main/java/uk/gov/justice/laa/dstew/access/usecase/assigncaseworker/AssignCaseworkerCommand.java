package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Input command for the assignCaseworker use case. */
@Builder(toBuilder = true)
public record AssignCaseworkerCommand(
    UUID caseworkerId, List<UUID> applicationIds, String eventDescription) {}

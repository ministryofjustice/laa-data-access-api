package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.model;

import java.util.UUID;
import lombok.Builder;

/** Projection carrying the only application fields required by the assignCaseworker use case. */
@Builder(toBuilder = true)
public record AssignCaseworkerApplication(UUID id, UUID caseworkerId) {}
